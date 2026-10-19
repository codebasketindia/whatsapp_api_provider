package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.messaging.dto.MessageDtos.CostEstimateDto;
import in.codebasket.cbwa.messaging.dto.MessageDtos.CountryBreakdown;
import in.codebasket.cbwa.messaging.dto.MessageDtos.EstimateRequest;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MediaUploadDto;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** MS1 estimate, MS2 send and MS6 media upload for the partner dashboard. */
@Service
@RequiredArgsConstructor
public class PartnerMessagingService {

    /** Meta Cloud API media limits (bytes) by detected MIME type. */
    private static final Map<String, Long> MEDIA_LIMITS = Map.ofEntries(
            Map.entry("image/jpeg", 5L << 20), Map.entry("image/png", 5L << 20),
            Map.entry("image/webp", 500L << 10),
            Map.entry("video/mp4", 16L << 20), Map.entry("video/3gpp", 16L << 20),
            Map.entry("audio/aac", 16L << 20), Map.entry("audio/amr", 16L << 20), Map.entry("audio/mpeg", 16L << 20),
            Map.entry("audio/mp4", 16L << 20), Map.entry("audio/ogg", 16L << 20),
            Map.entry("application/pdf", 16L << 20), Map.entry("text/plain", 16L << 20),
            Map.entry("application/msword", 16L << 20),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", 16L << 20),
            Map.entry("application/vnd.ms-excel", 16L << 20),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 16L << 20),
            Map.entry("application/x-tika-ooxml", 16L << 20), Map.entry("application/x-tika-msoffice", 16L << 20),
            Map.entry("application/vnd.ms-powerpoint", 16L << 20),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", 16L << 20));

    private final OutboundMessageService outbound;
    private final MessagePayloadBuilder payloadBuilder;
    private final SenderAccessService senderAccess;
    private final WalletService walletService;
    private final MessageMapper mapper;
    private final FileStorageService storage;
    private final MetaGraphClient graph;

    @Transactional(readOnly = true)
    public CostEstimateDto estimate(Long partnerId, EstimateRequest req) {
        PhoneNumber sender = senderAccess.requirePartnerSender(partnerId, req.senderId());
        MessageCategory category = MessageCategory.SERVICE;
        if ("template".equalsIgnoreCase(req.type())) {
            if (req.templateId() == null && (req.templateName() == null || req.templateName().isBlank())) {
                throw AppException.field("templateId", "Choose a template");
            }
            category = payloadBuilder.resolveTemplate(partnerId, sender, new SendMessageRequest.TemplateRef(req.templateId(),
                    req.templateName(), req.templateLanguage(), null, null)).getCategory();
        }
        Map<String, Long> byCountry = new TreeMap<>();
        long invalid = 0;
        if (req.recipients() != null && !req.recipients().isEmpty()) {
            Set<String> seen = new java.util.HashSet<>();
            for (String r : req.recipients()) {
                var parsed = PhoneUtil.parse(r, "IN");
                if (parsed.isEmpty()) {
                    invalid++;
                    continue;
                }
                if (seen.add(parsed.get().digits())) {
                    byCountry.merge(parsed.get().regionCode() == null ? "ZZ" : parsed.get().regionCode(), 1L, Long::sum);
                }
            }
        } else if (req.count() != null) {
            byCountry.put(req.countryCode() == null || req.countryCode().isBlank() ? "IN" : req.countryCode().toUpperCase(), req.count().longValue());
        } else {
            throw AppException.field("recipients", "Add recipients or a count");
        }
        List<CountryBreakdown> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var e : byCountry.entrySet()) {
            String sample = sampleNumber(e.getKey());
            BigDecimal unit = sample == null
                    ? outbound.unitPrice(partnerId, sender.getId(), category, null)
                    : outbound.unitPrice(partnerId, sender.getId(), category, sample);
            BigDecimal sub = MoneyUtil.scale(unit.multiply(BigDecimal.valueOf(e.getValue())));
            rows.add(new CountryBreakdown(e.getKey(), e.getValue(), unit, sub));
            total = total.add(sub);
        }
        total = MoneyUtil.scale(total);
        BigDecimal available = walletService.get(partnerId).getBalance();
        long count = byCountry.values().stream().mapToLong(Long::longValue).sum();
        return new CostEstimateDto(category.name(), rows, count, invalid, total, available,
                MoneyUtil.scale(available.subtract(total)), available.compareTo(total) >= 0);
    }

    @Transactional
    public MessageDto send(Long partnerId, SendMessageRequest req, MessageSource source) {
        if (req.senderId() == null) {
            throw AppException.field("senderId", "Choose a sender number");
        }
        if (req.to() == null || req.to().isBlank()) {
            throw AppException.field("to", "Enter the recipient's WhatsApp number");
        }
        Message m = outbound.enqueue(OutboundCommand.of(partnerId, req.senderId(), OutboundMessageServiceImpl.normaliseTo(req.to()), req, source));
        return mapper.toDtos(List.of(m), false).get(0);
    }

    @Transactional
    public MediaUploadDto uploadMedia(Long partnerId, Long senderId, MultipartFile file) {
        PhoneNumber sender = senderAccess.requireUsableSender(partnerId, senderId);
        StoredFile f = storage.store(file, FileCategory.MEDIA_OUTBOUND, partnerId, MEDIA_LIMITS.keySet(), 16L << 20);
        Long limit = MEDIA_LIMITS.getOrDefault(f.getMimeType(), 16L << 20);
        if (f.getSizeBytes() > limit) {
            storage.delete(f);
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "WhatsApp allows at most " + (limit >= (1 << 20) ? (limit >> 20) + " MB" : (limit >> 10) + " KB")
                    + " for this file type");
        }
        String mediaId = graph.uploadMedia(sender.getPhoneNumberId(), storage.readBytes(f), f.getMimeType(), f.getOriginalName(),
                senderAccess.accessToken(sender));
        return new MediaUploadDto(f.getUid(), mediaId, f.getMimeType(), f.getSizeBytes());
    }

    /** A valid example number for a region, used to price by country without real recipients. */
    private static String sampleNumber(String region) {
        if ("ZZ".equals(region)) {
            return null;
        }
        var util = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        var ex = util.getExampleNumberForType(region, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType.MOBILE);
        return ex == null ? null : ex.getCountryCode() + String.valueOf(ex.getNationalNumber());
    }
}
