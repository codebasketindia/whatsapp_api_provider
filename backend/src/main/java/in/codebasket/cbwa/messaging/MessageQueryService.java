package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import in.codebasket.cbwa.messaging.lookup.MsgCampaignRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** MS3-MS5 / ML1-ML3: message log search, detail and CSV/XLSX export. */
@Service
@RequiredArgsConstructor
public class MessageQueryService {

    private static final int MAX_EXPORT_DAYS = 31;
    private static final int MAX_EXPORT_ROWS = 500_000;
    private static final DateTimeFormatter IST_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(TimeUtil.IST);

    private final MessageRepository messageRepository;
    private final MessageMapper mapper;
    private final MsgCampaignRepository campaignRepository;

    public record Filter(Long partnerId, String direction, String status, Long senderId, String category, String type,
                         String campaignUid, String search, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageDto> list(Filter f, Integer page, Integer size, String sort, boolean admin) {
        var pageable = Paging.of(page, size, sort, Set.of("createdAt", "status", "price"), Sort.by(Sort.Direction.DESC, "id"));
        Page<Message> result = messageRepository.findAll(spec(f), pageable);
        List<MessageDto> dtos = mapper.toDtos(result.getContent(), admin);
        return new PageResponse<>(dtos, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MessageDto detail(String uid, Long partnerId, boolean admin) {
        Message m = (partnerId == null ? messageRepository.findByUid(uid) : messageRepository.findByUidAndPartnerId(uid, partnerId))
                .orElseThrow(() -> AppException.notFound("Message"));
        return mapper.toDetail(m, admin);
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter f, String format, boolean admin) {
        LocalDate to = f.to() != null ? f.to() : TimeUtil.todayIst();
        LocalDate from = f.from() != null ? f.from() : to.minusDays(6);
        if (ChronoUnit.DAYS.between(from, to) >= MAX_EXPORT_DAYS) {
            throw AppException.field("from", "Export at most " + MAX_EXPORT_DAYS + " days at a time");
        }
        Filter bounded = new Filter(f.partnerId(), f.direction(), f.status(), f.senderId(), f.category(), f.type(),
                f.campaignUid(), f.search(), from, to);
        String[] headers = admin
                ? new String[]{"Message ID", "Partner", "Date (IST)", "Direction", "Sender", "Recipient", "Type", "Category", "Template",
                "Status", "Error", "Price", "Charge", "Source", "Client reference", "WhatsApp ID", "Text"}
                : new String[]{"Message ID", "Date (IST)", "Direction", "Sender", "Recipient", "Type", "Category", "Template",
                "Status", "Error", "Price", "Charge", "Source", "Client reference", "WhatsApp ID", "Text"};
        List<List<String>> rows = new ArrayList<>();
        int pageNo = 0;
        while (rows.size() < MAX_EXPORT_ROWS) {
            Page<Message> p = messageRepository.findAll(spec(bounded), PageRequest.of(pageNo++, 1000, Sort.by(Sort.Direction.DESC, "id")));
            for (MessageDto d : mapper.toDtos(p.getContent(), admin)) {
                List<String> r = new ArrayList<>();
                r.add(d.uid());
                if (admin) {
                    r.add(d.partner() == null ? "" : d.partner().businessName());
                }
                r.add(IST_FMT.format(d.createdAt()));
                r.add(d.direction());
                r.add(d.sender() == null ? "" : d.sender().displayPhoneNumber());
                r.add(d.waId());
                r.add(d.type());
                r.add(nz(d.category()));
                r.add(d.template() == null ? "" : d.template().name());
                r.add(d.status());
                r.add(nz(d.errorMessage()));
                r.add(d.price().toPlainString());
                r.add(d.chargeStatus());
                r.add(d.source());
                r.add(nz(d.clientReference()));
                r.add(nz(d.wamid()));
                r.add(nz(d.preview()));
                rows.add(r);
            }
            if (!p.hasNext()) {
                break;
            }
        }
        try {
            return "xlsx".equalsIgnoreCase(format) ? xlsx(headers, rows) : csv(headers, rows);
        } catch (IOException e) {
            throw new IllegalStateException("Export failed", e);
        }
    }

    Specification<Message> spec(Filter f) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.partnerId() != null) {
                ps.add(cb.equal(root.get("partnerId"), f.partnerId()));
            }
            if (notBlank(f.direction())) {
                ps.add(cb.equal(root.get("direction"), enumOf(MessageDirection.class, f.direction(), "direction")));
            }
            if (notBlank(f.status())) {
                ps.add(cb.equal(root.get("status"), enumOf(MessageStatus.class, f.status(), "status")));
            }
            if (f.senderId() != null) {
                ps.add(cb.equal(root.get("phoneNumberId"), f.senderId()));
            }
            if (notBlank(f.category())) {
                ps.add(cb.equal(root.get("category"), enumOf(MessageCategory.class, f.category(), "category")));
            }
            if (notBlank(f.type())) {
                ps.add(cb.equal(root.get("type"), enumOf(MessageType.class, f.type(), "type")));
            }
            if (notBlank(f.campaignUid())) {
                Long cid = campaignRepository.findByUid(f.campaignUid()).map(c -> c.getId()).orElse(-1L);
                ps.add(cb.equal(root.get("campaignId"), cid));
            }
            if (notBlank(f.search())) {
                String s = f.search().trim();
                String digits = s.replaceAll("[^0-9]", "");
                List<Predicate> or = new ArrayList<>();
                if (digits.length() >= 4) {
                    or.add(cb.like(root.get("waId"), "%" + digits + "%"));
                }
                or.add(cb.equal(root.get("wamid"), s));
                or.add(cb.equal(root.get("uid"), s));
                or.add(cb.equal(root.get("clientReference"), s));
                ps.add(cb.or(or.toArray(Predicate[]::new)));
            }
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeUtil.startOfDayIst(f.from())));
            }
            if (f.to() != null) {
                ps.add(cb.lessThan(root.<Instant>get("createdAt"), TimeUtil.endOfDayIstExclusive(f.to())));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private static byte[] csv(String[] headers, List<List<String>> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM so Excel shows ₹ and Indic text
        try (CSVPrinter p = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader(headers).build())) {
            for (List<String> r : rows) {
                p.printRecord(r.stream().map(MessageQueryService::csvSafe).toList());
            }
        }
        return out.toByteArray();
    }

    /** Prevents spreadsheet formula injection in exported cells. */
    static String csvSafe(String v) {
        if (v != null && !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?")) {
            return "'" + v;
        }
        return v;
    }

    private static byte[] xlsx(String[] headers, List<List<String>> rows) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            Sheet sheet = wb.createSheet("Messages");
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (List<String> row : rows) {
                Row x = sheet.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    x.createCell(i).setCellValue(row.get(i) == null ? "" : row.get(i));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        }
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw AppException.field(field, "Invalid " + field);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
