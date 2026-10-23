package in.codebasket.cbwa.support;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.auth.TokenService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.partner.PartnerSettings;
import in.codebasket.cbwa.partner.PartnerSettingsRepository;
import in.codebasket.cbwa.partner.PartnerStatus;
import in.codebasket.cbwa.pricing.RatePlan;
import in.codebasket.cbwa.pricing.RatePlanItem;
import in.codebasket.cbwa.pricing.RatePlanItemRepository;
import in.codebasket.cbwa.pricing.RatePlanRepository;
import in.codebasket.cbwa.sender.AssignmentType;
import in.codebasket.cbwa.sender.PartnerPhoneNumber;
import in.codebasket.cbwa.sender.PartnerPhoneNumberRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderStatus;
import in.codebasket.cbwa.user.Role;
import in.codebasket.cbwa.user.RoleRepository;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import in.codebasket.cbwa.user.UserStatus;
import in.codebasket.cbwa.user.UserType;
import in.codebasket.cbwa.waba.OnboardingType;
import in.codebasket.cbwa.waba.TokenStatus;
import in.codebasket.cbwa.waba.TokenType;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WabaStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixture builder shared by all integration tests. Creates rows directly through repositories. */
@Component
@RequiredArgsConstructor
public class TestData {

    public static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerSettingsRepository partnerSettingsRepository;
    private final WalletRepository walletRepository;
    private final RatePlanRepository ratePlanRepository;
    private final RatePlanItemRepository ratePlanItemRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CryptoService crypto;
    private final SettingsService settingsService;
    private final in.codebasket.cbwa.auth.LoginRateLimiter loginRateLimiter;

    public void resetCaches() {
        settingsService.evictAll();
        loginRateLimiter.cleanup();
    }

    @Transactional
    public User admin(String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User u = new User();
        u.setUid(Ulids.next());
        u.setUserType(UserType.ADMIN);
        u.setRole(role);
        u.setName("Admin " + SEQ.incrementAndGet());
        int an = SEQ.incrementAndGet();
        u.setEmail("admin" + an + "@test.local");
        u.setMobile("9198" + String.format("%08d", an));
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    public User superAdmin() {
        return admin("SUPER_ADMIN");
    }

    /** Active partner with user, settings and a wallet holding the given balance. */
    @Transactional
    public Partner partner(BigDecimal balance) {
        int n = SEQ.incrementAndGet();
        User u = new User();
        u.setUid(Ulids.next());
        u.setUserType(UserType.PARTNER);
        u.setRole(roleRepository.findByCode("API_PARTNER").orElseThrow());
        u.setName("Partner User " + n);
        u.setEmail("partner" + n + "@test.local");
        u.setMobile("9197" + String.format("%08d", n));
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setStatus(UserStatus.ACTIVE);
        userRepository.save(u);

        Partner p = new Partner();
        p.setUid(Ulids.next());
        p.setPartnerCode("CBP" + String.format("%06d", n));
        p.setUserId(u.getId());
        p.setBusinessName("Business " + n);
        p.setContactName("Contact " + n);
        p.setEmail(u.getEmail());
        p.setMobile(u.getMobile());
        p.setStatus(PartnerStatus.ACTIVE);
        p.setRatePlanId(defaultPlan().getId());
        p.setAllowOwnNumber(true);
        p.setCountry("IN");
        partnerRepository.save(p);

        PartnerSettings ps = new PartnerSettings();
        ps.setPartnerId(p.getId());
        partnerSettingsRepository.save(ps);

        Wallet w = new Wallet();
        w.setPartnerId(p.getId());
        w.setBalance(MoneyUtil.scale(balance));
        w.setHeldAmount(MoneyUtil.ZERO);
        w.setCurrency("INR");
        walletRepository.save(w);
        return p;
    }

    public RatePlan defaultPlan() {
        return ratePlanRepository.findFirstByDefaultPlanTrue().orElseThrow();
    }

    /** Sets a price on the default plan (country "IN" or "*"). */
    @Transactional
    public void price(String country, MessageCategory category, String price) {
        RatePlan plan = defaultPlan();
        RatePlanItem item = ratePlanItemRepository.findByRatePlanIdAndCountryCodeAndCategory(plan.getId(), country, category)
                .orElseGet(RatePlanItem::new);
        item.setRatePlanId(plan.getId());
        item.setCountryCode(country);
        item.setCategory(category);
        item.setPrice(new BigDecimal(price));
        ratePlanItemRepository.save(item);
    }

    @Transactional
    public WhatsappAccount waba(WabaOwnerType ownerType, Long partnerId, BillingMode mode) {
        WhatsappAccount w = new WhatsappAccount();
        w.setWabaId("1" + System.nanoTime() % 1_000_000_000_000L);
        w.setOwnerType(ownerType);
        w.setPartnerId(partnerId);
        w.setName("Test WABA");
        w.setAccessTokenEnc(crypto.encrypt("TEST_TOKEN"));
        w.setTokenType(ownerType == WabaOwnerType.PLATFORM ? TokenType.SYSTEM_USER : TokenType.BUSINESS_INTEGRATION);
        w.setTokenStatus(TokenStatus.VALID);
        w.setWebhookSubscribed(true);
        w.setOnboardingType(ownerType == WabaOwnerType.PLATFORM ? OnboardingType.MANUAL : OnboardingType.EMBEDDED_SIGNUP);
        w.setBillingMode(mode);
        w.setStatus(WabaStatus.ACTIVE);
        w.setConnectedAt(Instant.now());
        return wabaRepository.save(w);
    }

    /** Connected sender on the WABA, assigned (or owned) by the partner. */
    @Transactional
    public PhoneNumber sender(WhatsappAccount waba, Long partnerId, SenderType type) {
        PhoneNumber pn = new PhoneNumber();
        pn.setWhatsappAccountId(waba.getId());
        pn.setPhoneNumberId("2" + System.nanoTime() % 1_000_000_000_000L);
        pn.setDisplayPhoneNumber("+91 98765 43210");
        pn.setWaNumber("919876543210");
        pn.setVerifiedName("Test Sender");
        pn.setQualityRating("GREEN");
        pn.setSenderType(type);
        pn.setRegistered(true);
        pn.setStatus(SenderStatus.CONNECTED);
        pn.setMaxMps(60);
        pn.setConnectedAt(Instant.now());
        phoneNumberRepository.save(pn);
        if (partnerId != null) {
            PartnerPhoneNumber a = new PartnerPhoneNumber();
            a.setPartnerId(partnerId);
            a.setPhoneNumberId(pn.getId());
            a.setAssignmentType(type == SenderType.PLATFORM_SENDER ? AssignmentType.ASSIGNED : AssignmentType.OWN);
            a.setDefaultSender(true);
            a.setActive(true);
            a.setAssignedAt(Instant.now());
            assignmentRepository.save(a);
        }
        return pn;
    }

    @Transactional
    public String accessToken(User user) {
        return tokenService.issue(user, null, null).accessToken();
    }

    @Transactional
    public String accessToken(Partner partner) {
        return accessToken(userRepository.findById(partner.getUserId()).orElseThrow());
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static JsonNode json(String body) {
        return JsonUtil.readTree(body);
    }
}
