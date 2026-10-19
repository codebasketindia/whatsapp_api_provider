package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.wallet.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformFeeJobIT extends AbstractIntegrationTest {

    @Autowired
    PlatformFeeJob job;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    PhoneNumberRepository phoneNumberRepository;

    @AfterEach
    void resetPlan() {
        jdbc.update("update rate_plans set platform_fee_monthly_per_number = 0 where is_default = true");
    }

    @Test
    void chargesOncePerMonthAndPausesAfterGraceDays() {
        jdbc.update("update rate_plans set platform_fee_monthly_per_number = 300 where is_default = true");
        Partner rich = testData.partner(new BigDecimal("1000"));
        WhatsappAccount w1 = testData.waba(WabaOwnerType.PARTNER, rich.getId(), BillingMode.TECH_PROVIDER);
        testData.sender(w1, rich.getId(), SenderType.PARTNER_OWN_SENDER);

        Partner poor = testData.partner(new BigDecimal("10"));
        WhatsappAccount w2 = testData.waba(WabaOwnerType.PARTNER, poor.getId(), BillingMode.TECH_PROVIDER);
        PhoneNumber poorNumber = testData.sender(w2, poor.getId(), SenderType.PARTNER_OWN_SENDER);

        LocalDate day = LocalDate.of(2030, 1, 1);
        PlatformFeeJob.Summary s1 = job.runFor(day);
        assertThat(s1.charged()).isEqualTo(1);
        assertThat(s1.insufficient()).isEqualTo(1);
        assertThat(walletRepository.findByPartnerId(rich.getId()).orElseThrow().getBalance()).isEqualByComparingTo("700");

        // next days: no double charge for the rich partner, retries for the poor one until paused
        for (int i = 1; i <= 7; i++) {
            job.runFor(day.plusDays(i));
        }
        assertThat(walletRepository.findByPartnerId(rich.getId()).orElseThrow().getBalance()).isEqualByComparingTo("700");
        PhoneNumber paused = phoneNumberRepository.findById(poorNumber.getId()).orElseThrow();
        assertThat(paused.isPaused()).isTrue();
        assertThat(paused.getPauseReason()).isEqualTo(PlatformFeeJob.PAUSE_REASON);

        // partner adds money -> next run charges and resumes the number
        jdbc.update("update wallets set balance = 500 where partner_id = ?", poor.getId());
        job.runFor(day.plusDays(9));
        PhoneNumber resumed = phoneNumberRepository.findById(poorNumber.getId()).orElseThrow();
        assertThat(resumed.isPaused()).isFalse();
        assertThat(walletRepository.findByPartnerId(poor.getId()).orElseThrow().getBalance()).isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject("select count(*) from platform_fee_charges where status = 'CHARGED'", Long.class)).isEqualTo(2);
    }
}
