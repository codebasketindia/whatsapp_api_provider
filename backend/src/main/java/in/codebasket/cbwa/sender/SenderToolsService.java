package in.codebasket.cbwa.sender;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.meta.MetaToolsApi;
import in.codebasket.cbwa.meta.MetaWabaApi;
import in.codebasket.cbwa.sender.dto.SenderDtos.AnalyticsDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.BusinessProfileDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.HealthDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.MetaAnalyticsRow;
import in.codebasket.cbwa.sender.dto.SenderDtos.OurAnalyticsRow;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import in.codebasket.cbwa.waba.TokenStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** T1-T11: phone number management, verification, PIN, business profile, health and analytics. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SenderToolsService {

    private static final Set<String> VERTICALS = Set.of("UNDEFINED", "OTHER", "AUTO", "BEAUTY", "APPAREL", "EDU", "ENTERTAIN",
            "EVENT_PLAN", "FINANCE", "GROCERY", "GOVT", "HOTEL", "HEALTH", "NONPROFIT", "PROF_SERVICES", "RETAIL", "TRAVEL",
            "RESTAURANT", "NOT_A_BIZ");

    private final PhoneNumberRepository phoneNumberRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final BusinessProfileRepository profileRepository;
    private final SenderSyncService syncService;
    private final SenderMapper mapper;
    private final MetaWabaApi wabaApi;
    private final MetaToolsApi toolsApi;
    private final MetaGraphClient graphClient;
    private final CryptoService crypto;
    private final FileStorageService storage;
    private final AuditService auditService;
    private final SenderStatsRepository statsRepository;
    private final TransactionTemplate tx;
    private final PartnerPhoneNumberRepository assignmentRepository;

    /** T1 */
    public SenderDto sync(PhoneNumber pn) {
        syncService.syncSender(pn);
        return tx.execute(st -> mapper.toDto(phoneNumberRepository.findById(pn.getId()).orElseThrow()));
    }

    /** T2 */
    public void requestCode(PhoneNumber pn, String method, String language) {
        wabaApi.requestCode(pn.getPhoneNumberId(), method, language == null || language.isBlank() ? "en" : language, token(pn));
    }

    /** T3 */
    public SenderDto verifyCode(PhoneNumber pn, String code) {
        wabaApi.verifyCode(pn.getPhoneNumberId(), code, token(pn));
        return tx.execute(st -> {
            PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
            fresh.setCodeVerificationStatus("VERIFIED");
            phoneNumberRepository.save(fresh);
            return mapper.toDto(fresh);
        });
    }

    /** T4 */
    public SenderDto register(PhoneNumber pn, String pin) {
        if (pn.isCoexistence()) {
            throw AppException.conflict("Numbers on the WhatsApp Business App don't need registration");
        }
        wabaApi.register(pn.getPhoneNumberId(), pin, token(pn));
        return tx.execute(st -> {
            PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
            fresh.setRegistered(true);
            fresh.setPinEnc(crypto.encrypt(pin));
            if (fresh.getStatus() == SenderStatus.PENDING_SETUP) {
                fresh.setStatus(SenderStatus.CONNECTED);
            }
            phoneNumberRepository.save(fresh);
            auditService.log("SENDER_REGISTER", "SENDER", fresh.getId(), null, Map.of("phoneNumberId", fresh.getPhoneNumberId()));
            return mapper.toDto(fresh);
        });
    }

    /** T5 */
    public SenderDto deregister(PhoneNumber pn) {
        wabaApi.deregister(pn.getPhoneNumberId(), token(pn));
        return tx.execute(st -> {
            PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
            fresh.setRegistered(false);
            if (fresh.getStatus() == SenderStatus.CONNECTED) {
                fresh.setStatus(SenderStatus.PENDING_SETUP);
            }
            phoneNumberRepository.save(fresh);
            auditService.log("SENDER_DEREGISTER", "SENDER", fresh.getId(), null, Map.of("phoneNumberId", fresh.getPhoneNumberId()));
            return mapper.toDto(fresh);
        });
    }

    /** T6 */
    public void changePin(PhoneNumber pn, String pin) {
        wabaApi.setTwoStepPin(pn.getPhoneNumberId(), pin, token(pn));
        tx.executeWithoutResult(st -> {
            PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
            fresh.setPinEnc(crypto.encrypt(pin));
            phoneNumberRepository.save(fresh);
            auditService.log("SENDER_PIN_CHANGE", "SENDER", fresh.getId(), null, null);
        });
    }

    /** T7: live from Meta (cached locally), falls back to the cache when Meta is unreachable. */
    public BusinessProfileDto getProfile(PhoneNumber pn) {
        try {
            JsonNode p = toolsApi.getProfile(pn.getPhoneNumberId(), token(pn));
            return tx.execute(st -> toDto(saveProfile(pn.getId(), p)));
        } catch (MetaApiException e) {
            BusinessProfile cached = profileRepository.findByPhoneNumberId(pn.getId()).orElse(null);
            if (cached == null) {
                throw e;
            }
            return toDto(cached);
        }
    }

    /** T8 */
    public BusinessProfileDto updateProfile(PhoneNumber pn, BusinessProfileDto req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("about", nz(req.about()));
        fields.put("description", nz(req.description()));
        fields.put("address", nz(req.address()));
        fields.put("email", nz(req.email()));
        List<String> sites = req.websites() == null ? List.of() : req.websites().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        for (String s : sites) {
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                throw AppException.field("websites", "Websites must start with http:// or https://");
            }
        }
        fields.put("websites", sites);
        if (req.vertical() != null && !req.vertical().isBlank()) {
            if (!VERTICALS.contains(req.vertical())) {
                throw AppException.field("vertical", "Unknown business category");
            }
            fields.put("vertical", req.vertical());
        }
        if (req.email() != null && !req.email().isBlank() && !req.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw AppException.field("email", "Enter a valid email");
        }
        toolsApi.updateProfile(pn.getPhoneNumberId(), fields, token(pn));
        auditLater(pn, "SENDER_PROFILE_UPDATE");
        return getProfile(pn);
    }

    /** T9: profile photo via the Resumable Upload API handle. */
    public BusinessProfileDto uploadPhoto(PhoneNumber pn, MultipartFile file) {
        StoredFile f = storage.store(file, FileCategory.PROFILE_PHOTO, partnerOf(pn), FileStorageService.IMAGES, 5L * 1024 * 1024);
        byte[] bytes = storage.readBytes(f);
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img != null && (img.getWidth() < 192 || img.getHeight() < 192)) {
                throw AppException.field("file", "Photo must be at least 192 x 192 pixels");
            }
        } catch (java.io.IOException ignored) {
            // size check is best effort; Meta validates too
        }
        String token = token(pn);
        String handle = graphClient.resumableUpload(bytes, f.getMimeType(), f.getOriginalName(), token);
        toolsApi.updateProfile(pn.getPhoneNumberId(), Map.of("profile_picture_handle", handle), token);
        tx.executeWithoutResult(st -> {
            BusinessProfile bp = profileRepository.findByPhoneNumberId(pn.getId()).orElseGet(() -> newProfile(pn.getId()));
            bp.setProfilePictureFileId(f.getId());
            profileRepository.save(bp);
        });
        auditLater(pn, "SENDER_PROFILE_PHOTO");
        return getProfile(pn);
    }

    /** T10 */
    public HealthDto health(PhoneNumber pn) {
        WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
        List<String> alerts = new ArrayList<>();
        try {
            JsonNode h = wabaApi.getPhoneHealth(pn.getPhoneNumberId(), token(pn));
            tx.executeWithoutResult(st -> syncService.persistPhone(pn.getId(), h));
        } catch (MetaApiException e) {
            alerts.add("Live health check failed: " + e.friendlyMessage());
        }
        PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
        WhatsappAccount freshWaba = wabaRepository.findById(waba.getId()).orElseThrow();
        if ("RED".equals(fresh.getQualityRating())) {
            alerts.add("Quality is low (red). Recipients are blocking or reporting your messages - send only to opted-in customers.");
        } else if ("YELLOW".equals(fresh.getQualityRating())) {
            alerts.add("Quality is medium (yellow). Review your message content and frequency.");
        }
        if (fresh.isPaused()) {
            alerts.add("Sending is paused: " + fresh.getPauseReason());
        }
        if (freshWaba.getTokenStatus() == TokenStatus.INVALID || freshWaba.getTokenStatus() == TokenStatus.EXPIRED) {
            alerts.add("Access to this WhatsApp account expired - reconnect the number.");
        }
        var health = SenderMapper.health(fresh.getHealthStatus());
        health.errors().forEach(e -> alerts.add((e.description() == null ? "Health issue" : e.description())
                + (e.solution() == null ? "" : " - " + e.solution())));
        return new HealthDto(fresh.getQualityRating(), fresh.getMessagingLimitTier(), fresh.getThroughputLevel(), health,
                fresh.getNameStatus(), freshWaba.getTokenStatus().name(), alerts);
    }

    /** T11 */
    public AnalyticsDto analytics(PhoneNumber pn, LocalDate from, LocalDate to) {
        Instant[] range = TimeUtil.range(from, to, 30);
        List<OurAnalyticsRow> ours = new ArrayList<>();
        for (Object[] r : statsRepository.dailyStats(pn.getId(), range[0], range[1])) {
            ours.add(new OurAnalyticsRow(String.valueOf(r[0]), num(r[1]), num(r[2]), num(r[3]), num(r[4]),
                    MoneyUtil.scale(r[5] == null ? BigDecimal.ZERO : new BigDecimal(r[5].toString()))));
        }
        List<MetaAnalyticsRow> meta = new ArrayList<>();
        try {
            WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
            JsonNode res = wabaApi.pricingAnalytics(waba.getWabaId(), range[0].getEpochSecond(), range[1].getEpochSecond(),
                    pn.getWaNumber(), crypto.decrypt(waba.getAccessTokenEnc()));
            for (JsonNode dataSet : res.path("pricing_analytics").path("data")) {
                for (JsonNode dp : dataSet.path("data_points")) {
                    String date = TimeUtil.toIstDate(Instant.ofEpochSecond(dp.path("start").asLong())).toString();
                    meta.add(new MetaAnalyticsRow(date, dp.path("pricing_category").asText(null), dp.path("pricing_type").asText(null),
                            dp.path("country").asText(null), dp.path("volume").asLong(), MoneyUtil.scale(new BigDecimal(dp.path("cost").asText("0")))));
                }
            }
        } catch (MetaApiException | NumberFormatException e) {
            log.debug("Pricing analytics unavailable for sender {}: {}", pn.getId(), e.getMessage());
        }
        return new AnalyticsDto(meta, ours);
    }

    private String token(PhoneNumber pn) {
        WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow(() -> AppException.notFound("WhatsApp account"));
        return crypto.decrypt(waba.getAccessTokenEnc());
    }

    private Long partnerOf(PhoneNumber pn) {
        return assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).map(PartnerPhoneNumber::getPartnerId).orElse(null);
    }

    private void auditLater(PhoneNumber pn, String action) {
        tx.executeWithoutResult(st -> auditService.log(action, "SENDER", pn.getId(), null, null));
    }

    private BusinessProfile saveProfile(Long senderId, JsonNode p) {
        BusinessProfile bp = profileRepository.findByPhoneNumberId(senderId).orElseGet(() -> newProfile(senderId));
        bp.setAbout(p.path("about").asText(null));
        bp.setDescription(p.path("description").asText(null));
        bp.setAddress(p.path("address").asText(null));
        bp.setEmail(p.path("email").asText(null));
        List<String> sites = new ArrayList<>();
        p.path("websites").forEach(w -> sites.add(w.asText()));
        bp.setWebsites(sites);
        bp.setVertical(p.path("vertical").asText(null));
        bp.setProfilePictureUrl(p.path("profile_picture_url").asText(null));
        bp.setSyncedAt(Instant.now());
        return profileRepository.save(bp);
    }

    private static BusinessProfile newProfile(Long senderId) {
        BusinessProfile bp = new BusinessProfile();
        bp.setPhoneNumberId(senderId);
        return bp;
    }

    private static BusinessProfileDto toDto(BusinessProfile bp) {
        return new BusinessProfileDto(bp.getAbout(), bp.getDescription(), bp.getAddress(), bp.getEmail(),
                bp.getWebsites() == null ? List.of() : bp.getWebsites(), bp.getVertical(), bp.getProfilePictureUrl());
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
