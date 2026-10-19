package in.codebasket.cbwa.waba;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.meta.MetaSettings;
import in.codebasket.cbwa.meta.MetaSettingsService;
import in.codebasket.cbwa.meta.MetaWabaApi;
import in.codebasket.cbwa.sender.ChecklistService;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderMapper;
import in.codebasket.cbwa.sender.SenderStatus;
import in.codebasket.cbwa.sender.SenderSyncService;
import in.codebasket.cbwa.sender.dto.SenderDtos.ChecklistDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.ConnectResultDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.ManualConnectRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import in.codebasket.cbwa.waba.dto.WabaDtos.HealthOverviewDto;
import in.codebasket.cbwa.waba.dto.WabaDtos.TokenTestDto;
import in.codebasket.cbwa.waba.dto.WabaDtos.WabaDetailDto;
import in.codebasket.cbwa.waba.dto.WabaDtos.WabaDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** M4-M12, M18: WhatsApp Business Accounts (ours and partners'). */
@Service
@RequiredArgsConstructor
public class WabaService {

    private final WhatsappAccountRepository wabaRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final MetaWabaApi wabaApi;
    private final MetaGraphClient graphClient;
    private final MetaSettingsService metaSettings;
    private final CryptoService crypto;
    private final WabaMapper wabaMapper;
    private final SenderMapper senderMapper;
    private final ChecklistService checklistService;
    private final SenderSyncService syncService;
    private final OnboardingService onboardingService;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    public PageResponse<WabaDto> list(String ownerType, String status, String tokenStatus, Long partnerId, Integer page, Integer size) {
        Specification<WhatsappAccount> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (ownerType != null && !ownerType.isBlank()) {
                ps.add(cb.equal(root.get("ownerType"), WabaOwnerType.valueOf(ownerType)));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), WabaStatus.valueOf(status)));
            }
            if (tokenStatus != null && !tokenStatus.isBlank()) {
                ps.add(cb.equal(root.get("tokenStatus"), TokenStatus.valueOf(tokenStatus)));
            }
            if (partnerId != null) {
                ps.add(cb.equal(root.get("partnerId"), partnerId));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return tx.execute(st -> PageResponse.of(wabaRepository.findAll(spec,
                Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"))), wabaMapper::toDto));
    }

    /** M5: add our own WABA with a system-user token and import its numbers as platform senders. */
    public WabaDetailDto addPlatformWaba(String wabaId, String token) {
        if (wabaRepository.findByWabaId(wabaId).isPresent()) {
            throw AppException.conflict("This WhatsApp account is already added");
        }
        Instant expiresAt = tokenExpiry(token);
        JsonNode wabaJson = wabaApi.getWaba(wabaId, token);
        List<JsonNode> phones = wabaApi.listPhoneNumbers(wabaId, token);
        boolean subscribed;
        try {
            wabaApi.subscribeApp(wabaId, token);
            subscribed = true;
        } catch (MetaApiException e) {
            subscribed = false;
        }
        final boolean sub = subscribed;
        Long id = tx.execute(st -> {
            WhatsappAccount w = new WhatsappAccount();
            w.setWabaId(wabaId);
            w.setOwnerType(WabaOwnerType.PLATFORM);
            WabaMapper.apply(w, wabaJson);
            w.setAccessTokenEnc(crypto.encrypt(token));
            w.setTokenType(TokenType.SYSTEM_USER);
            w.setTokenExpiresAt(expiresAt);
            w.setTokenStatus(TokenStatus.VALID);
            w.setTokenCheckedAt(Instant.now());
            w.setWebhookSubscribed(sub);
            w.setOnboardingType(OnboardingType.MANUAL);
            w.setBillingMode(BillingMode.PLATFORM);
            w.setStatus(WabaStatus.ACTIVE);
            w.setConnectedAt(Instant.now());
            wabaRepository.save(w);
            for (JsonNode p : phones) {
                String pnid = p.path("id").asText();
                if (phoneNumberRepository.findByPhoneNumberId(pnid).isPresent()) {
                    continue;
                }
                PhoneNumber pn = new PhoneNumber();
                pn.setWhatsappAccountId(w.getId());
                pn.setPhoneNumberId(pnid);
                SenderMapper.apply(pn, p);
                pn.setSenderType(SenderType.PLATFORM_SENDER);
                boolean connected = "CONNECTED".equalsIgnoreCase(p.path("status").asText());
                pn.setRegistered(connected);
                pn.setStatus(connected ? SenderStatus.CONNECTED : SenderStatus.PENDING_SETUP);
                pn.setMaxMps(60);
                pn.setConnectedAt(Instant.now());
                phoneNumberRepository.save(pn);
            }
            auditService.log("WABA_ADD", "WABA", w.getId(), null, Map.of("wabaId", wabaId, "numbers", phones.size()));
            return w.getId();
        });
        return detail(id);
    }

    public WabaDetailDto detail(Long id) {
        return tx.execute(st -> {
            WhatsappAccount w = get(id);
            List<PhoneNumber> phones = phoneNumberRepository.findByWhatsappAccountId(id);
            List<SenderDto> senders = phones.stream().map(senderMapper::toDto).toList();
            ChecklistDto checklist = phones.isEmpty() ? new ChecklistDto(List.of()) : checklistService.build(phones.get(0), w);
            return new WabaDetailDto(wabaMapper.toDto(w), senders, checklist);
        });
    }

    /** M7: replace the access token (validated first). */
    public WabaDto updateToken(Long id, String token) {
        WhatsappAccount w = get(id);
        wabaApi.getWaba(w.getWabaId(), token);
        Instant expiresAt = tokenExpiry(token);
        return tx.execute(st -> {
            WhatsappAccount fresh = get(id);
            fresh.setAccessTokenEnc(crypto.encrypt(token));
            fresh.setTokenStatus(TokenStatus.VALID);
            fresh.setTokenExpiresAt(expiresAt);
            fresh.setTokenCheckedAt(Instant.now());
            wabaRepository.save(fresh);
            auditService.log("WABA_TOKEN_UPDATE", "WABA", id, null, Map.of("wabaId", fresh.getWabaId()), fresh.getPartnerId());
            return wabaMapper.toDto(fresh);
        });
    }

    /** M8 */
    public WabaDto sync(Long id) {
        syncService.syncWaba(get(id));
        return tx.execute(st -> wabaMapper.toDto(get(id)));
    }

    /** M9: token validity via /debug_token plus subscription state. */
    public TokenTestDto testToken(Long id) {
        WhatsappAccount w = get(id);
        String token = crypto.decrypt(w.getAccessTokenEnc());
        boolean valid;
        List<String> scopes = new ArrayList<>();
        Instant expires = null;
        try {
            JsonNode data = graphClient.debugToken(token);
            valid = data.path("is_valid").asBoolean(false);
            data.path("scopes").forEach(s -> scopes.add(s.asText()));
            long exp = data.path("expires_at").asLong(0);
            expires = exp > 0 ? Instant.ofEpochSecond(exp) : null;
        } catch (MetaApiException e) {
            valid = false;
        } catch (AppException e) {
            // App credentials not configured: fall back to a direct call with the token
            try {
                wabaApi.getWaba(w.getWabaId(), token);
                valid = true;
            } catch (MetaApiException ex) {
                valid = false;
            }
        }
        boolean subscribed = false;
        if (valid) {
            try {
                subscribed = wabaApi.subscribedApps(w.getWabaId(), token).path("data").size() > 0;
            } catch (MetaApiException ignored) {
                subscribed = w.isWebhookSubscribed();
            }
        }
        final boolean v = valid;
        final boolean sub = subscribed;
        final Instant exp = expires;
        tx.executeWithoutResult(st -> {
            WhatsappAccount fresh = get(id);
            fresh.setTokenStatus(v ? TokenStatus.VALID : TokenStatus.INVALID);
            fresh.setTokenCheckedAt(Instant.now());
            fresh.setTokenExpiresAt(exp);
            fresh.setWebhookSubscribed(sub);
            wabaRepository.save(fresh);
        });
        return new TokenTestDto(valid, scopes, expires, subscribed);
    }

    /** M10 */
    public WabaDto subscribe(Long id) {
        WhatsappAccount w = get(id);
        wabaApi.subscribeApp(w.getWabaId(), crypto.decrypt(w.getAccessTokenEnc()));
        return tx.execute(st -> {
            WhatsappAccount fresh = get(id);
            fresh.setWebhookSubscribed(true);
            wabaRepository.save(fresh);
            auditService.log("WABA_SUBSCRIBE", "WABA", id, null, Map.of("wabaId", fresh.getWabaId()), fresh.getPartnerId());
            return wabaMapper.toDto(fresh);
        });
    }

    /** M11: Solution Partner credit line sharing. */
    public WabaDto shareCreditLine(Long id) {
        WhatsappAccount w = get(id);
        MetaSettings ms = metaSettings.get();
        if (ms.getBillingMode() != BillingMode.SOLUTION_PARTNER || ms.getExtendedCreditLineId() == null) {
            throw AppException.conflict("Credit line sharing needs Solution Partner billing mode with a credit line ID");
        }
        if (w.getOwnerType() != WabaOwnerType.PARTNER) {
            throw AppException.conflict("Only partner accounts use a shared credit line");
        }
        if (w.isCreditLineShared()) {
            throw AppException.conflict("Credit line is already shared");
        }
        wabaApi.shareCreditLine(ms.getExtendedCreditLineId(), w.getWabaId(), crypto.decrypt(w.getAccessTokenEnc()));
        return tx.execute(st -> {
            WhatsappAccount fresh = get(id);
            fresh.setCreditLineShared(true);
            fresh.setBillingMode(BillingMode.SOLUTION_PARTNER);
            wabaRepository.save(fresh);
            auditService.log("WABA_CREDIT_LINE_SHARE", "WABA", id, null, Map.of("wabaId", fresh.getWabaId()), fresh.getPartnerId());
            return wabaMapper.toDto(fresh);
        });
    }

    /** M12: admin connects a WABA + number with a permanent token. */
    public ConnectResultDto manualConnect(ManualConnectRequest req) {
        String token = req.accessToken().trim();
        if (req.register() && (req.pin() == null || req.pin().isBlank())) {
            throw AppException.field("pin", "A 6-digit PIN is needed to register the number");
        }
        try {
            JsonNode data = graphClient.debugToken(token);
            if (!data.path("is_valid").asBoolean(true)) {
                throw AppException.field("accessToken", "This access token is not valid");
            }
        } catch (AppException e) {
            if (e.getErrors() != null) {
                throw e;
            }
            // app credentials missing - the WABA lookup below still validates the token
        }
        return onboardingService.connect(new OnboardingService.Command(req.partnerId(), token, TokenType.SYSTEM_USER,
                OnboardingType.MANUAL, req.wabaId(), req.phoneNumberId(), null, OnboardingService.Mode.EXISTING,
                req.pin(), req.register(), List.of()));
    }

    /** M18: account health panel. */
    public HealthOverviewDto health() {
        return tx.execute(st -> {
            List<Map<String, Object>> wabas = new ArrayList<>();
            List<Map<String, Object>> senders = new ArrayList<>();
            List<String> alerts = new ArrayList<>();
            for (WhatsappAccount w : wabaRepository.findByStatusNot(WabaStatus.DISCONNECTED)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("waba", wabaMapper.toDto(w));
                m.put("tokenStatus", w.getTokenStatus().name());
                m.put("reviewStatus", w.getAccountReviewStatus());
                m.put("restrictions", w.getRestrictionInfo() == null ? null : in.codebasket.cbwa.common.util.JsonUtil.readTree(w.getRestrictionInfo()));
                wabas.add(m);
                if (w.getTokenStatus() == TokenStatus.INVALID || w.getTokenStatus() == TokenStatus.EXPIRED) {
                    alerts.add("Access token invalid for WhatsApp account " + nameOf(w) + " - reconnect needed");
                }
                if (w.getStatus() == WabaStatus.RESTRICTED) {
                    alerts.add("WhatsApp account " + nameOf(w) + " is restricted by Meta");
                }
            }
            for (PhoneNumber pn : phoneNumberRepository.findAll()) {
                if (pn.getStatus() == SenderStatus.DISCONNECTED) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sender", senderMapper.toDto(pn));
                m.put("quality", pn.getQualityRating());
                m.put("health", SenderMapper.health(pn.getHealthStatus()));
                senders.add(m);
                if ("RED".equals(pn.getQualityRating()) || "YELLOW".equals(pn.getQualityRating())) {
                    alerts.add("Sender " + pn.getDisplayPhoneNumber() + " quality is " + pn.getQualityRating());
                }
                if (pn.isPaused()) {
                    alerts.add("Sender " + pn.getDisplayPhoneNumber() + " is paused: " + pn.getPauseReason());
                }
            }
            return new HealthOverviewDto(wabas, senders, alerts);
        });
    }

    private Instant tokenExpiry(String token) {
        try {
            JsonNode d = graphClient.debugToken(token);
            if (d.has("is_valid") && !d.path("is_valid").asBoolean()) {
                throw AppException.field("accessToken", "This access token is not valid");
            }
            long exp = d.path("expires_at").asLong(0);
            return exp > 0 ? Instant.ofEpochSecond(exp) : null;
        } catch (MetaApiException e) {
            return null;
        } catch (AppException e) {
            if (e.getErrors() != null) {
                throw e;
            }
            return null;
        }
    }

    private WhatsappAccount get(Long id) {
        return wabaRepository.findById(id).orElseThrow(() -> AppException.notFound("WhatsApp account"));
    }

    private static String nameOf(WhatsappAccount w) {
        return w.getName() == null ? w.getWabaId() : w.getName();
    }
}
