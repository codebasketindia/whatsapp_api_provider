package in.codebasket.cbwa.waba;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.meta.MetaWabaApi;
import in.codebasket.cbwa.sender.SenderSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * F9 jobs: token health every 6 hours and phone-number sync (quality, tier, name, health) every hour.
 * Disabled with cbwa.jobs.enabled=false (scheduling is not enabled then).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WabaHealthJobs {

    private final WhatsappAccountRepository wabaRepository;
    private final MetaGraphClient graphClient;
    private final MetaWabaApi wabaApi;
    private final CryptoService crypto;
    private final SenderSyncService syncService;
    private final TransactionTemplate tx;

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT6H")
    public void tokenHealth() {
        for (WhatsappAccount w : wabaRepository.findByStatusNot(WabaStatus.DISCONNECTED)) {
            try {
                checkToken(w);
            } catch (RuntimeException e) {
                log.warn("Token health check failed for WABA {}: {}", w.getWabaId(), e.getMessage());
            }
        }
    }

    /** Uses /debug_token when the app is configured, otherwise a direct WABA read. */
    public TokenStatus checkToken(WhatsappAccount w) {
        String token = crypto.decrypt(w.getAccessTokenEnc());
        TokenStatus status;
        Instant expires = w.getTokenExpiresAt();
        try {
            JsonNode d = graphClient.debugToken(token);
            boolean valid = d.path("is_valid").asBoolean(false);
            long exp = d.path("expires_at").asLong(0);
            expires = exp > 0 ? Instant.ofEpochSecond(exp) : null;
            status = valid ? TokenStatus.VALID : (expires != null && expires.isBefore(Instant.now()) ? TokenStatus.EXPIRED : TokenStatus.INVALID);
        } catch (AppException e) {
            try {
                wabaApi.getWaba(w.getWabaId(), token);
                status = TokenStatus.VALID;
            } catch (MetaApiException ex) {
                status = ex.isTokenInvalid() ? TokenStatus.INVALID : w.getTokenStatus();
            }
        } catch (MetaApiException e) {
            status = w.getTokenStatus();
        }
        final TokenStatus result = status;
        final Instant exp = expires;
        tx.executeWithoutResult(st -> wabaRepository.findById(w.getId()).ifPresent(fresh -> {
            boolean becameBad = fresh.getTokenStatus() == TokenStatus.VALID && result != TokenStatus.VALID;
            fresh.setTokenStatus(result);
            fresh.setTokenExpiresAt(exp);
            fresh.setTokenCheckedAt(Instant.now());
            wabaRepository.save(fresh);
            if (becameBad) {
                syncService.alertTokenInvalid(fresh);
            }
        }));
        return result;
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT1H")
    public void senderSync() {
        for (WhatsappAccount w : wabaRepository.findByStatusNot(WabaStatus.DISCONNECTED)) {
            if (w.getTokenStatus() == TokenStatus.INVALID || w.getTokenStatus() == TokenStatus.EXPIRED) {
                continue;
            }
            try {
                syncService.syncWaba(w);
            } catch (RuntimeException e) {
                log.warn("Sender sync failed for WABA {}: {}", w.getWabaId(), e.getMessage());
            }
        }
    }
}
