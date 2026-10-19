package in.codebasket.cbwa.partner;

import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.PasswordPolicy;
import in.codebasket.cbwa.auth.RefreshTokenRepository;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.auth.TokenService;
import in.codebasket.cbwa.auth.dto.AuthDtos.TokenDto;
import in.codebasket.cbwa.campaign.CampaignStatus;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.sequence.NumberSequenceService;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.partner.dto.PartnerDtos.AddressDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.CreatePartnerRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerDetailDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerSettingsDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerStatsDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.UpdatePartnerRequest;
import in.codebasket.cbwa.pricing.RatePlan;
import in.codebasket.cbwa.pricing.RatePlanRepository;
import in.codebasket.cbwa.pricing.RatePlanStatus;
import in.codebasket.cbwa.user.RoleRepository;
import in.codebasket.cbwa.user.StaffService;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import in.codebasket.cbwa.user.UserStatus;
import in.codebasket.cbwa.user.UserType;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletRepository;
import in.codebasket.cbwa.wallet.WalletService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phase 3: partner CRUD, KYC, activate/suspend, own-number toggle, rate plan, login-as-partner. */
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final PartnerSettingsRepository settingsRepository;
    private final PartnerCampaignControlRepository campaignControl;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RatePlanRepository ratePlanRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final NumberSequenceService sequences;
    private final PasswordEncoder passwordEncoder;
    private final StaffService staffService;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final PartnerMapper mapper;
    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<PartnerDto> list(String search, String status, Long ratePlanId, Boolean lowBalance,
                                         Integer page, Integer size, String sort) {
        BigDecimal defaultThreshold = settings.getDecimal(SettingKeys.WALLET, SettingKeys.DEFAULT_LOW_BALANCE, new BigDecimal("500"));
        Specification<Partner> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), PartnerStatus.valueOf(status)));
            }
            if (ratePlanId != null) {
                ps.add(cb.equal(root.get("ratePlanId"), ratePlanId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("businessName")), like), cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("contactName")), like), cb.like(root.get("mobile"), like),
                        cb.like(cb.lower(root.get("partnerCode")), like)));
            }
            if (Boolean.TRUE.equals(lowBalance)) {
                Subquery<Long> sq = q.subquery(Long.class);
                Root<Wallet> w = sq.from(Wallet.class);
                Root<PartnerSettings> s = sq.from(PartnerSettings.class);
                sq.select(w.get("id")).where(cb.equal(w.get("partnerId"), root.get("id")),
                        cb.equal(s.get("partnerId"), root.get("id")),
                        cb.lessThan(w.get("balance"), cb.coalesce(s.<BigDecimal>get("lowBalanceThreshold"), defaultThreshold)));
                ps.add(cb.exists(sq));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var pageable = Paging.of(page, size, sort, Set.of("businessName", "createdAt", "partnerCode", "status"),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<Partner> result = partnerRepository.findAll(spec, pageable);
        List<PartnerDto> dtos = mapper.toDtos(result.getContent());
        return new PageResponse<>(dtos, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PartnerDetailDto detail(Long id) {
        Partner p = require(id);
        PartnerSettings s = settingsRepository.findById(id).orElse(null);
        BigDecimal def = settings.getDecimal(SettingKeys.WALLET, SettingKeys.DEFAULT_LOW_BALANCE, new BigDecimal("500"));
        PartnerSettingsDto sd = new PartnerSettingsDto(s == null ? null : s.getLowBalanceThreshold(),
                s == null || s.getLowBalanceThreshold() == null ? MoneyUtil.scale(def) : s.getLowBalanceThreshold(),
                s == null ? List.of() : s.getOptOutKeywords(), s == null ? null : s.getDefaultPhoneNumberId(),
                s == null || s.isNotifyEmail());
        return PartnerDetailDto.of(mapper.toDto(p), sd, stats(id));
    }

    public PartnerStatsDto stats(Long partnerId) {
        Instant dayStart = TimeUtil.startOfDayIst(TimeUtil.todayIst());
        Instant monthStart = TimeUtil.startOfMonthIst(YearMonth.from(TimeUtil.todayIst()));
        MapSqlParameterSource params = new MapSqlParameterSource("pid", partnerId)
                .addValue("day", Timestamp.from(dayStart)).addValue("month", Timestamp.from(monthStart));
        Long senders = jdbc.queryForObject(
                "select count(*) from partner_phone_numbers where partner_id = :pid and active = true", params, Long.class);
        Map<String, Object> row = jdbc.queryForMap("""
                select coalesce(sum(case when created_at >= :day then 1 else 0 end), 0) as today,
                       count(*) as month,
                       coalesce(sum(case when charge_status = 'CHARGED' then price else 0 end), 0) as spend
                from messages where partner_id = :pid and direction = 'OUTBOUND' and created_at >= :month
                """, params);
        return new PartnerStatsDto(senders == null ? 0 : senders, ((Number) row.get("today")).longValue(),
                ((Number) row.get("month")).longValue(), MoneyUtil.scale(new BigDecimal(row.get("spend").toString())));
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public PartnerDto create(CreatePartnerRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw AppException.field("email", "This email is already registered");
        }
        RatePlan plan = resolvePlan(req.ratePlanId(), null);
        String password = req.password();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            password = PasswordPolicy.temporary();
        } else {
            PasswordPolicy.check(password);
        }

        User u = new User();
        u.setUid(Ulids.next());
        u.setUserType(UserType.PARTNER);
        u.setRole(roleRepository.findByCode("API_PARTNER").orElseThrow());
        u.setName(req.contactName().trim());
        u.setEmail(email);
        u.setMobile(req.mobile().trim());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePassword(true);
        userRepository.save(u);

        Partner p = new Partner();
        p.setUid(Ulids.next());
        p.setPartnerCode(sequences.nextFormatted("PARTNER_CODE", "CBP", 6));
        p.setUserId(u.getId());
        p.setStatus(PartnerStatus.ACTIVE);
        p.setRatePlanId(plan.getId());
        p.setAllowOwnNumber(req.allowOwnNumber());
        applyProfile(p, req.businessName(), req.contactName(), email, req.mobile(), req.gstin(), req.pan(), req.address(), req.website());
        partnerRepository.save(p);

        PartnerSettings ps = new PartnerSettings();
        ps.setPartnerId(p.getId());
        ps.setLowBalanceThreshold(req.lowBalanceThreshold() == null ? null : MoneyUtil.scale(req.lowBalanceThreshold()));
        settingsRepository.save(ps);

        walletService.createWallet(p.getId());

        auditService.log("PARTNER_CREATE", "PARTNER", p.getId(), null,
                Map.of("partnerCode", p.getPartnerCode(), "businessName", p.getBusinessName(), "email", email), p.getId());
        if (generated || !Boolean.FALSE.equals(req.sendWelcomeEmail())) {
            staffService.sendCredentials(u, password);
        }
        return mapper.toDto(p);
    }

    @Transactional
    public PartnerDto update(Long id, UpdatePartnerRequest req) {
        Partner p = require(id);
        PartnerDto old = mapper.toDto(p);
        String email = req.email().trim().toLowerCase();
        User u = userRepository.findById(p.getUserId()).orElseThrow();
        if (!u.getEmail().equalsIgnoreCase(email)) {
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw AppException.field("email", "This email is already registered");
            }
            u.setEmail(email);
        }
        u.setName(req.contactName().trim());
        u.setMobile(req.mobile().trim());
        userRepository.save(u);

        applyProfile(p, req.businessName(), req.contactName(), email, req.mobile(), req.gstin(), req.pan(), req.address(), req.website());
        if (req.ratePlanId() != null && !req.ratePlanId().equals(p.getRatePlanId())) {
            if (!SecurityUtils.current().hasPermission("PRICING_MANAGE")) {
                throw AppException.forbidden();
            }
            p.setRatePlanId(resolvePlan(req.ratePlanId(), p.getId()).getId());
        }
        p.setAllowOwnNumber(req.allowOwnNumber());
        partnerRepository.save(p);

        PartnerSettings ps = settingsRepository.findById(id).orElseGet(() -> {
            PartnerSettings n = new PartnerSettings();
            n.setPartnerId(id);
            return n;
        });
        ps.setLowBalanceThreshold(req.lowBalanceThreshold() == null ? null : MoneyUtil.scale(req.lowBalanceThreshold()));
        settingsRepository.save(ps);

        PartnerDto updated = mapper.toDto(p);
        auditService.log("PARTNER_UPDATE", "PARTNER", id, old, updated, id);
        return updated;
    }

    @Transactional
    public PartnerDto setStatus(Long id, String status, String reason) {
        Partner p = require(id);
        PartnerStatus target = PartnerStatus.valueOf(status);
        PartnerStatus old = p.getStatus();
        if (target == PartnerStatus.SUSPENDED) {
            if (reason == null || reason.isBlank()) {
                throw AppException.field("reason", "Please give a reason for suspending");
            }
            p.setSuspendReason(reason.trim());
            refreshTokenRepository.revokeAllForUser(p.getUserId(), Instant.now());
            campaignControl.pauseAll(p.getId(), List.of(CampaignStatus.RUNNING, CampaignStatus.SCHEDULED),
                    CampaignStatus.PAUSED, "ADMIN", Instant.now());
        } else {
            p.setSuspendReason(null);
        }
        p.setStatus(target);
        partnerRepository.save(p);
        Map<String, Object> newVal = new LinkedHashMap<>();
        newVal.put("status", target);
        newVal.put("reason", reason);
        auditService.log("PARTNER_STATUS", "PARTNER", id, Map.of("status", old), newVal, id);
        if (target == PartnerStatus.ACTIVE && old == PartnerStatus.SUSPENDED) {
            notifications.notifyPartner(id, "ACCOUNT_ACTIVATED", NotificationService.SUCCESS, "Your account is active again",
                    "Your account has been re-activated. Paused campaigns stay paused until you resume them.", "/partner", p.getEmail());
        } else if (target == PartnerStatus.SUSPENDED && old == PartnerStatus.ACTIVE) {
            notifications.email(p.getEmail(), "Your account is suspended",
                    "Your CB WhatsApp Gateway account has been suspended. Reason: " + reason + ". Please contact support.", null);
        }
        return mapper.toDto(p);
    }

    @Transactional
    public PartnerDto assignRatePlan(Long id, Long ratePlanId) {
        Partner p = require(id);
        Long old = p.getRatePlanId();
        RatePlan plan = resolvePlan(ratePlanId, id);
        p.setRatePlanId(plan.getId());
        partnerRepository.save(p);
        auditService.log("RATE_PLAN_ASSIGN", "PARTNER", id, Map.of("ratePlanId", old), Map.of("ratePlanId", plan.getId()), id);
        return mapper.toDto(p);
    }

    @Transactional
    public PartnerDto setOwnNumber(Long id, boolean allowed) {
        Partner p = require(id);
        boolean old = p.isAllowOwnNumber();
        p.setAllowOwnNumber(allowed);
        partnerRepository.save(p);
        auditService.log("OWN_NUMBER_TOGGLE", "PARTNER", id, Map.of("allowOwnNumber", old), Map.of("allowOwnNumber", allowed), id);
        return mapper.toDto(p);
    }

    @Transactional
    public PartnerDto setKyc(Long id, boolean verified) {
        Partner p = require(id);
        boolean old = p.isKycVerified();
        p.setKycVerified(verified);
        partnerRepository.save(p);
        auditService.log("PARTNER_KYC", "PARTNER", id, Map.of("kycVerified", old), Map.of("kycVerified", verified), id);
        return mapper.toDto(p);
    }

    @Transactional
    public void resetPassword(Long id) {
        Partner p = require(id);
        User u = userRepository.findById(p.getUserId()).orElseThrow();
        String password = PasswordPolicy.temporary();
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMustChangePassword(true);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        userRepository.save(u);
        refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        auditService.log("PARTNER_PASSWORD_RESET", "PARTNER", id, null, null, id);
        staffService.sendCredentials(u, password);
    }

    /** P10: issues partner tokens carrying the admin as impersonator (1 hour refresh). Audited. */
    @Transactional
    public TokenDto loginAs(Long id, String reason) {
        AuthPrincipal admin = SecurityUtils.current();
        Partner p = require(id);
        if (p.getStatus() != PartnerStatus.ACTIVE) {
            throw new AppException(ErrorCode.PARTNER_SUSPENDED, "This partner is suspended. Activate it first");
        }
        User u = userRepository.findById(p.getUserId()).orElseThrow();
        if (u.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.AUTH_ACCOUNT_DISABLED, "The partner's login is disabled");
        }
        auditService.log("LOGIN_AS_PARTNER", "PARTNER", id, null, Map.of("reason", reason.trim(), "partnerCode", p.getPartnerCode()), id);
        return tokenService.issue(u, admin.userId(), null);
    }

    public Partner require(Long id) {
        return partnerRepository.findById(id).orElseThrow(() -> AppException.notFound("Partner"));
    }

    private RatePlan resolvePlan(Long ratePlanId, Long partnerId) {
        RatePlan plan = ratePlanId == null
                ? ratePlanRepository.findFirstByDefaultPlanTrue().orElseThrow(() -> AppException.field("ratePlanId", "No default rate plan"))
                : ratePlanRepository.findById(ratePlanId).orElseThrow(() -> AppException.field("ratePlanId", "Rate plan not found"));
        if (plan.getStatus() != RatePlanStatus.ACTIVE) {
            throw AppException.field("ratePlanId", "This rate plan is archived");
        }
        if (plan.getPartnerId() != null && !plan.getPartnerId().equals(partnerId)) {
            throw AppException.field("ratePlanId", "This custom plan belongs to another partner");
        }
        return plan;
    }

    private static void applyProfile(Partner p, String businessName, String contactName, String email, String mobile,
                                     String gstin, String pan, AddressDto address, String website) {
        p.setBusinessName(businessName.trim());
        p.setContactName(contactName.trim());
        p.setEmail(email);
        p.setMobile(mobile.trim());
        p.setGstin(blank(gstin) ? null : gstin.trim().toUpperCase());
        p.setPan(blank(pan) ? null : pan.trim().toUpperCase());
        p.setWebsite(blank(website) ? null : website.trim());
        if (address != null) {
            p.setAddressLine1(trim(address.line1()));
            p.setAddressLine2(trim(address.line2()));
            p.setCity(trim(address.city()));
            p.setState(trim(address.state()));
            p.setStateCode(trim(address.stateCode()));
            p.setPincode(trim(address.pincode()));
            p.setCountry(blank(address.country()) ? "IN" : address.country().trim());
        } else if (p.getCountry() == null) {
            p.setCountry("IN");
        }
        // GSTIN starts with the GST state code - use it when no state code was given.
        if (p.getStateCode() == null && p.getGstin() != null) {
            p.setStateCode(p.getGstin().substring(0, 2));
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }
}
