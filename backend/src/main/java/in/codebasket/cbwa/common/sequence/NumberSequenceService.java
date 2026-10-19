package in.codebasket.cbwa.common.sequence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Gap-free counters (partner codes, top-up numbers, invoice numbers). The counter row is locked
 * for the duration of the caller's transaction, so a rollback does not burn a number.
 */
@Service
@RequiredArgsConstructor
public class NumberSequenceService {

    private final NumberSequenceRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String name, String prefixIfNew) {
        NumberSequence seq = repository.lockByName(name).orElseGet(() -> create(name, prefixIfNew));
        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        seq.setUpdatedAt(Instant.now());
        return value;
    }

    /** Formats e.g. prefix "CBP" + 6 digits -> CBP000123. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextFormatted(String name, String prefix, int digits) {
        long v = next(name, prefix);
        return prefix + String.format("%0" + digits + "d", v);
    }

    private NumberSequence create(String name, String prefix) {
        NumberSequence seq = new NumberSequence();
        seq.setName(name);
        seq.setPrefix(prefix);
        seq.setNextValue(1);
        seq.setUpdatedAt(Instant.now());
        try {
            repository.saveAndFlush(seq);
        } catch (DataIntegrityViolationException e) {
            // created concurrently - fall through to lock the existing row
        }
        return repository.lockByName(name).orElseThrow();
    }
}
