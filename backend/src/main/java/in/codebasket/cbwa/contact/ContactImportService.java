package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.contact.dto.ContactDtos.ImportMapping;
import in.codebasket.cbwa.contact.dto.ContactDtos.ImportPreview;
import in.codebasket.cbwa.contact.dto.ContactDtos.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** C5-C7: CSV / Excel contact import with a downloadable per-row validation report. */
@Service
@RequiredArgsConstructor
public class ContactImportService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_ROWS = 50_000;
    private static final int CHUNK = 500;

    private final ContactRepository contactRepository;
    private final OptOutRepository optOutRepository;
    private final ContactGroupService groupService;
    private final FileStorageService storage;

    public ImportPreview preview(MultipartFile file) {
        TabularFile table = read(file, MAX_ROWS);
        return new ImportPreview(table.columns(), sample(table), table.rows().size());
    }

    public static List<List<String>> sample(TabularFile table) {
        List<List<String>> sample = new ArrayList<>();
        for (int i = 0; i < Math.min(5, table.rows().size()); i++) {
            sample.add(new ArrayList<>(table.rowMap(i).values()));
        }
        return sample;
    }

    public byte[] sampleCsv() {
        String csv = "﻿phone,name,email,tags,city\n919876543210,Ravi Kumar,ravi@example.com,customer;vip,Pune\n"
                + "+91 98765 43211,Asha,asha@example.com,customer,Mumbai\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ImportResult importContacts(Long partnerId, MultipartFile file, String mappingJson, Long groupId,
                                       String defaultCountry, boolean updateExisting) {
        ImportMapping mapping;
        try {
            mapping = JsonUtil.fromJson(mappingJson, ImportMapping.class);
        } catch (IllegalArgumentException e) {
            throw AppException.field("mapping", "Column mapping is not valid");
        }
        if (mapping == null || mapping.phone() == null || mapping.phone().isBlank()) {
            throw AppException.field("mapping", "Choose the column that contains phone numbers");
        }
        if (groupId != null) {
            groupService.find(partnerId, groupId);
        }
        String region = defaultCountry == null || defaultCountry.isBlank() ? "IN" : defaultCountry.trim().toUpperCase();
        TabularFile table = read(file, MAX_ROWS);
        requireColumn(table, mapping.phone());
        Map<String, String> attrColumns = new LinkedHashMap<>();
        if (mapping.attributes() != null) {
            for (Map.Entry<String, String> e : mapping.attributes().entrySet()) {
                requireColumn(table, e.getKey());
                String key = e.getValue() == null ? "" : e.getValue().trim().toLowerCase();
                if (!ContactService.ATTRIBUTE_KEY.matcher(key).matches()) {
                    throw AppException.field("mapping", "Attribute names may use only a-z, 0-9 and _: " + e.getValue());
                }
                attrColumns.put(e.getKey(), key);
            }
        }
        for (String col : Arrays.asList(mapping.name(), mapping.email(), mapping.tags())) {
            if (col != null && !col.isBlank()) {
                requireColumn(table, col);
            }
        }

        List<List<String>> report = new ArrayList<>();
        Map<String, Map<String, String>> valid = new LinkedHashMap<>();
        Map<String, Integer> rowOfPhone = new HashMap<>();
        int invalid = 0;
        int duplicates = 0;
        for (int i = 0; i < table.rows().size(); i++) {
            Map<String, String> row = table.rowMap(i);
            int rowNo = i + 2; // header is row 1
            String raw = row.get(mapping.phone());
            var parsed = PhoneUtil.parse(raw, region);
            if (parsed.isEmpty()) {
                invalid++;
                report.add(List.of(String.valueOf(rowNo), raw == null ? "" : raw, "INVALID", "Not a valid phone number"));
                continue;
            }
            String phone = parsed.get().digits();
            if (valid.containsKey(phone)) {
                duplicates++;
                report.add(List.of(String.valueOf(rowNo), phone, "DUPLICATE", "Same number as row " + rowOfPhone.get(phone)));
                continue;
            }
            row.put("__region", parsed.get().regionCode());
            valid.put(phone, row);
            rowOfPhone.put(phone, rowNo);
        }

        Map<String, Contact> existing = new HashMap<>();
        Set<String> optedOut = new HashSet<>();
        List<String> phones = new ArrayList<>(valid.keySet());
        for (int i = 0; i < phones.size(); i += 1000) {
            List<String> chunk = phones.subList(i, Math.min(phones.size(), i + 1000));
            contactRepository.findByPartnerIdAndPhoneIn(partnerId, chunk).forEach(c -> existing.put(c.getPhone(), c));
            optOutRepository.findByPartnerIdAndPhoneIn(partnerId, chunk).forEach(o -> optedOut.add(o.getPhone()));
        }

        int created = 0;
        int updated = 0;
        List<Contact> toSave = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> e : valid.entrySet()) {
            String phone = e.getKey();
            Map<String, String> row = e.getValue();
            String rowNo = String.valueOf(rowOfPhone.get(phone));
            Contact c = existing.get(phone);
            if (c != null && !updateExisting) {
                duplicates++;
                report.add(List.of(rowNo, phone, "DUPLICATE", "Contact already exists"));
                continue;
            }
            boolean isNew = c == null;
            if (isNew) {
                c = new Contact();
                c.setPartnerId(partnerId);
                c.setPhone(phone);
                c.setCountryCode(row.get("__region"));
                c.setSource(ContactSource.IMPORT);
                c.setOptedOut(optedOut.contains(phone));
                c.setTags(new ArrayList<>());
                c.setAttributes(new LinkedHashMap<>());
            }
            String name = value(row, mapping.name());
            if (name != null) {
                c.setName(name.length() > 120 ? name.substring(0, 120) : name);
            }
            String email = value(row, mapping.email());
            if (email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") && email.length() <= 190) {
                c.setEmail(email);
            }
            String tags = value(row, mapping.tags());
            if (tags != null) {
                List<String> merged = new ArrayList<>(c.getTags() == null ? List.of() : c.getTags());
                merged.addAll(Arrays.asList(tags.split("[,;|]")));
                c.setTags(ContactService.cleanTags(merged));
            }
            if (!attrColumns.isEmpty()) {
                Map<String, String> attrs = new LinkedHashMap<>(c.getAttributes() == null ? Map.of() : c.getAttributes());
                attrColumns.forEach((col, key) -> {
                    String v = value(row, col);
                    if (v != null) {
                        attrs.put(key, v.length() > 500 ? v.substring(0, 500) : v);
                    }
                });
                c.setAttributes(attrs);
            }
            toSave.add(c);
            if (isNew) {
                created++;
                report.add(List.of(rowNo, phone, "CREATED", ""));
            } else {
                updated++;
                report.add(List.of(rowNo, phone, "UPDATED", ""));
            }
        }
        List<Long> savedIds = new ArrayList<>();
        for (int i = 0; i < toSave.size(); i += CHUNK) {
            contactRepository.saveAll(toSave.subList(i, Math.min(toSave.size(), i + CHUNK))).forEach(c -> savedIds.add(c.getId()));
            contactRepository.flush();
        }
        if (groupId != null && !savedIds.isEmpty()) {
            for (int i = 0; i < savedIds.size(); i += 5000) {
                groupService.addMembers(partnerId, groupId, savedIds.subList(i, Math.min(savedIds.size(), i + 5000)));
            }
        }
        report.sort((a, b) -> Integer.compare(Integer.parseInt(a.get(0)), Integer.parseInt(b.get(0))));
        byte[] csv = TabularFile.toCsv(List.of("row", "phone", "result", "message"), report);
        StoredFile reportFile = storage.store(csv, "contact-import-report-" + Instant.now().getEpochSecond() + ".csv",
                FileCategory.IMPORT, partnerId, null, 50L * 1024 * 1024);
        return new ImportResult(table.rows().size(), created, updated, invalid, duplicates, reportFile.getUid());
    }

    static TabularFile read(MultipartFile file, int maxRows) {
        if (file == null || file.isEmpty()) {
            throw AppException.field("file", "Please choose a CSV or Excel file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "File is larger than 10 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            String mime = name.endsWith(".xlsx") || isZip(bytes)
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv";
            if (name.endsWith(".xls") && !isZip(bytes)) {
                throw new AppException(ErrorCode.IMPORT_INVALID_FILE, "Old .xls files are not supported. Save as .xlsx or .csv");
            }
            return TabularFile.read(bytes, mime, maxRows);
        } catch (IOException e) {
            throw new AppException(ErrorCode.IMPORT_INVALID_FILE);
        }
    }

    static boolean isZip(byte[] b) {
        return b.length > 3 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
    }

    private static void requireColumn(TabularFile table, String col) {
        if (!table.columns().contains(col)) {
            throw AppException.field("mapping", "Column \"" + col + "\" is not in the file");
        }
    }

    private static String value(Map<String, String> row, String col) {
        if (col == null || col.isBlank()) {
            return null;
        }
        String v = row.get(col);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
