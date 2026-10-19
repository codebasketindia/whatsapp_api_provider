package in.codebasket.cbwa.topup;

import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.wallet.WalletQueryService;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletRepository;
import in.codebasket.cbwa.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopupAndWalletIT extends AbstractIntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0};

    @Autowired
    WalletQueryService walletQueryService;
    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    TopupService topupService;
    @Autowired
    SettingsService settingsService;

    @AfterEach
    void resetGst() {
        settingsService.set("GST", "enabled", "false");
    }

    private String today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
    }

    private String submit(String token, String amount, String utr) throws Exception {
        return mockMvc.perform(multipart("/api/partner/topup-requests")
                        .file(new MockMultipartFile("screenshot", "proof.png", "image/png", PNG))
                        .param("amount", amount).param("paymentMode", "UPI").param("utr", utr).param("paymentDate", today())
                        .header("Authorization", TestData.bearer(token)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void partnerRequestsAdminApprovesBalanceAndLedgerCorrect() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String pt = testData.accessToken(p);
        String at = testData.accessToken(testData.superAdmin());

        String created = submit(pt, "1000", "412345678901");
        long id = TestData.json(created).at("/data/id").asLong();
        assertThat(TestData.json(created).at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(TestData.json(created).at("/data/screenshotUrl").asText()).startsWith("/api/files/");

        // duplicate UTR (case/space normalised)
        mockMvc.perform(multipart("/api/partner/topup-requests")
                        .file(new MockMultipartFile("screenshot", "proof.png", "image/png", PNG))
                        .param("amount", "500").param("paymentMode", "UPI").param("utr", " 412345678901 ").param("paymentDate", today())
                        .header("Authorization", TestData.bearer(pt)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOPUP_DUPLICATE_UTR"));

        mockMvc.perform(post("/api/admin/topup-requests/" + id + "/approve").header("Authorization", TestData.bearer(at))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.walletTransaction.type").value("CREDIT"))
                .andExpect(jsonPath("$.data.walletTransaction.balanceAfter").value("1000.0000"));
        // double approval
        mockMvc.perform(post("/api/admin/topup-requests/" + id + "/approve").header("Authorization", TestData.bearer(at))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOPUP_ALREADY_PROCESSED"));
        mockMvc.perform(post("/api/admin/topup-requests/" + id + "/reject").header("Authorization", TestData.bearer(at))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"wrong\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/partner/wallet").header("Authorization", TestData.bearer(pt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value("1000.0000"))
                .andExpect(jsonPath("$.data.isLow").value(false));
        mockMvc.perform(get("/api/partner/wallet/transactions").header("Authorization", TestData.bearer(pt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].referenceType").value("TOPUP_REQUEST"))
                .andExpect(jsonPath("$.data.content[0].balanceBefore").value("0.0000"))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value("1000.0000"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'TOPUP_APPROVE'", Long.class)).isEqualTo(1);

        // passbook hides message-level rows by default
        walletService.hold(p.getId(), new BigDecimal("1"), WalletRefType.MESSAGE, "m1", null);
        mockMvc.perform(get("/api/partner/wallet/transactions").header("Authorization", TestData.bearer(pt)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/partner/wallet/transactions?includeMessageEntries=true").header("Authorization", TestData.bearer(pt)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/partner/wallet/transactions/export?format=xlsx").header("Authorization", TestData.bearer(pt)))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentApprovalsCreditOnlyOnce() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String created = submit(testData.accessToken(p), "250", "UTRCONC0001");
        long id = TestData.json(created).at("/data/id").asLong();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<Boolean> c = () -> {
                try {
                    topupService.approve(id, null);
                    return true;
                } catch (AppException e) {
                    return false;
                }
            };
            results.add(pool.submit(c));
        }
        int ok = 0;
        for (Future<Boolean> f : results) {
            ok += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(walletRepository.findByPartnerId(p.getId()).orElseThrow().getBalance()).isEqualByComparingTo("250");
        assertThat(jdbc.queryForObject("select count(*) from wallet_transactions where reference_type = 'TOPUP_REQUEST'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentManualDebitsNeverGoNegative() throws Exception {
        Partner p = testData.partner(new BigDecimal("100"));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> {
                try {
                    walletQueryService.adjust(p.getId(), "DEBIT", new BigDecimal("10"), "Concurrent debit test");
                    return true;
                } catch (AppException e) {
                    return false;
                }
            }));
        }
        int ok = 0;
        for (Future<Boolean> f : results) {
            ok += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(10);
        assertThat(walletRepository.findByPartnerId(p.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");
        // admin debit beyond the balance via API
        String at = testData.accessToken(testData.superAdmin());
        mockMvc.perform(post("/api/admin/partners/" + p.getId() + "/wallet/adjust").header("Authorization", TestData.bearer(at))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DEBIT\",\"amount\":\"1\",\"remarks\":\"too much\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WALLET_NEGATIVE_NOT_ALLOWED"));
        mockMvc.perform(post("/api/admin/partners/" + p.getId() + "/wallet/adjust").header("Authorization", TestData.bearer(at))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CREDIT\",\"amount\":\"5\",\"remarks\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("remarks"));
    }

    @Test
    void partnerCannotSeeAnotherPartnersRequestAndGstInvoiceIsCreated() throws Exception {
        settingsService.set("GST", "enabled", "true");
        Partner a = testData.partner(BigDecimal.ZERO);
        Partner b = testData.partner(BigDecimal.ZERO);
        String created = submit(testData.accessToken(a), "1180", "UTRGST00001");
        long id = TestData.json(created).at("/data/id").asLong();
        mockMvc.perform(get("/api/partner/topup-requests/" + id).header("Authorization", TestData.bearer(testData.accessToken(b))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/partner/topup-requests/" + id).header("Authorization", TestData.bearer(testData.accessToken(a))))
                .andExpect(status().isOk());

        topupService.approve(id, "ok");
        // GST-inclusive manual top-up: 1180 = 1000 taxable + 180 GST, full 1180 credited
        assertThat(walletRepository.findByPartnerId(a.getId()).orElseThrow().getBalance()).isEqualByComparingTo("1180");
        String token = testData.accessToken(a);
        mockMvc.perform(get("/api/partner/invoices").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].taxableAmount").value("1000.0000"))
                .andExpect(jsonPath("$.data.content[0].total").value("1180.0000"));
        long invoiceId = jdbc.queryForObject("select id from invoices limit 1", Long.class);
        byte[] pdf = mockMvc.perform(get("/api/partner/invoices/" + invoiceId + "/pdf").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        mockMvc.perform(get("/api/partner/invoices/" + invoiceId + "/pdf").header("Authorization", TestData.bearer(testData.accessToken(b))))
                .andExpect(status().isNotFound());
    }
}
