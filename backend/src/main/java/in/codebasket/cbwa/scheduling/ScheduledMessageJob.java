package in.codebasket.cbwa.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Sends due scheduled messages every 20 seconds. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageJob {

    private static final int BATCH = 200;

    private final ScheduledMessageService service;

    @Scheduled(fixedDelay = 20_000, initialDelay = 15_000)
    public void run() {
        runOnce();
    }

    /** Returns how many rows were processed (used by tests). */
    public int runOnce() {
        int failed = service.failStuck();
        if (failed > 0) {
            log.warn("{} scheduled messages were stuck in SENDING and were marked FAILED", failed);
        }
        int total = 0;
        List<Long> ids;
        do {
            ids = service.claimDue(BATCH);
            ids.forEach(service::dispatch);
            total += ids.size();
        } while (ids.size() == BATCH);
        return total;
    }
}
