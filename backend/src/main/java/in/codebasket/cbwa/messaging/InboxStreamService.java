package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.TokenGenerator;
import in.codebasket.cbwa.common.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live inbox updates (IN5/IN6). EventSource cannot send an Authorization header, so the partner
 * first gets a short-lived ticket with its JWT (IN6) and opens the stream with ?ticket=... .
 * Tickets are valid for 60 seconds (also for EventSource auto-reconnects within that window).
 */
@Slf4j
@Service
public class InboxStreamService {

    private static final long TICKET_TTL_SECONDS = 60;
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

    private record Ticket(Long partnerId, Instant expiresAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public Map<String, Object> createTicket(Long partnerId) {
        String ticket = TokenGenerator.urlSafe(24);
        tickets.put(ticket, new Ticket(partnerId, Instant.now().plusSeconds(TICKET_TTL_SECONDS)));
        return Map.of("ticket", ticket, "expiresIn", TICKET_TTL_SECONDS);
    }

    public SseEmitter open(String ticket) {
        Ticket t = ticket == null ? null : tickets.get(ticket);
        if (t == null || t.expiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.AUTH_TOKEN_INVALID, "Stream ticket is invalid or expired");
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(t.partnerId(), k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("ok", true)));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInboxEvent(InboxEvent event) {
        List<SseEmitter> list = emitters.get(event.partnerId());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(event.type()).data(event.data()));
            } catch (IOException | IllegalStateException ex) {
                list.remove(e);
            }
        }
    }

    /** Keeps proxies (Nginx) from closing idle streams and clears expired tickets. */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(en -> en.getValue().expiresAt().isBefore(now));
        emitters.values().forEach(list -> list.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                list.remove(e);
            }
        }));
    }

    int openStreams(Long partnerId) {
        return emitters.getOrDefault(partnerId, List.of()).size();
    }
}
