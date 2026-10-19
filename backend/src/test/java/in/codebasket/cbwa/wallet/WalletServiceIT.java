package in.codebasket.cbwa.wallet;

import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceIT extends AbstractIntegrationTest {

    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    WalletTransactionRepository txnRepository;

    @Test
    void concurrentHoldsNeverOverdraw() throws Exception {
        Partner p = testData.partner(new BigDecimal("10.0000"));
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> {
                try {
                    walletService.hold(p.getId(), new BigDecimal("0.5000"), WalletRefType.MESSAGE, "m", "test");
                    ok.incrementAndGet();
                } catch (AppException e) {
                    rejected.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        Wallet w = walletRepository.findByPartnerId(p.getId()).orElseThrow();
        assertThat(ok.get()).isEqualTo(20);
        assertThat(rejected.get()).isEqualTo(30);
        assertThat(w.getBalance()).isEqualByComparingTo("0");
        assertThat(w.getHeldAmount()).isEqualByComparingTo("10");
        assertLedgerChain(w);
    }

    @Test
    void holdFinalizeReleaseRefundKeepLedgerConsistent() {
        Partner p = testData.partner(new BigDecimal("100"));
        walletService.hold(p.getId(), new BigDecimal("1.25"), WalletRefType.MESSAGE, "a", null);
        walletService.hold(p.getId(), new BigDecimal("2.00"), WalletRefType.MESSAGE, "b", null);
        walletService.hold(p.getId(), new BigDecimal("3.00"), WalletRefType.MESSAGE, "c", null);
        walletService.finalizeHold(p.getId(), new BigDecimal("1.25"), WalletRefType.MESSAGE, "a", null);
        walletService.release(p.getId(), new BigDecimal("2.00"), WalletRefType.MESSAGE, "b", null);
        walletService.refundHold(p.getId(), new BigDecimal("3.00"), WalletRefType.MESSAGE, "c", null);
        Wallet w = walletRepository.findByPartnerId(p.getId()).orElseThrow();
        assertThat(w.getBalance()).isEqualByComparingTo("98.75");
        assertThat(w.getHeldAmount()).isEqualByComparingTo("0");
        assertLedgerChain(w);
        assertThatThrownBy(() -> walletService.debit(p.getId(), new BigDecimal("1000"), WalletRefType.ADMIN_ADJUSTMENT, null, "x"))
                .isInstanceOf(AppException.class);
    }

    private void assertLedgerChain(Wallet w) {
        List<WalletTransaction> txns = txnRepository.findByWalletIdOrderByIdAsc(w.getId());
        for (int i = 1; i < txns.size(); i++) {
            assertThat(txns.get(i).getBalanceBefore()).isEqualByComparingTo(txns.get(i - 1).getBalanceAfter());
            assertThat(txns.get(i).getHeldBefore()).isEqualByComparingTo(txns.get(i - 1).getHeldAfter());
        }
        WalletTransaction last = txns.get(txns.size() - 1);
        assertThat(last.getBalanceAfter()).isEqualByComparingTo(w.getBalance());
        assertThat(last.getHeldAfter()).isEqualByComparingTo(w.getHeldAmount());
    }
}
