package in.codebasket.cbwa.user;

import in.codebasket.cbwa.auth.PasswordPolicy;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

/**
 * On startup: makes sure SUPER_ADMIN holds every permission and creates the first super admin
 * from CBWA_SEED_ADMIN_EMAIL / CBWA_SEED_ADMIN_PASSWORD when none exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role superAdmin = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        superAdmin.setPermissions(new HashSet<>(permissionRepository.findAll()));
        roleRepository.save(superAdmin);

        String seedMobile = MobileNumbers.normaliseOrNull(props.getSeed().getAdminMobile());
        var existing = userRepository.findByUserTypeAndStatus(UserType.ADMIN, UserStatus.ACTIVE).stream().filter(User::isSuperAdmin).toList();
        if (!existing.isEmpty()) {
            // Upgrade path: super admins created before mobile login get the seed mobile so they can log in.
            User first = existing.get(0);
            if (first.getMobile() == null && seedMobile != null && userRepository.findByMobile(seedMobile).isEmpty()) {
                first.setMobile(seedMobile);
                userRepository.save(first);
                log.info("Super admin {} can now log in with the seed mobile number", first.getEmail());
            }
            return;
        }
        String email = props.getSeed().getAdminEmail();
        String password = props.getSeed().getAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank() || seedMobile == null) {
            log.warn("No super admin exists. Set CBWA_SEED_ADMIN_MOBILE, CBWA_SEED_ADMIN_EMAIL and CBWA_SEED_ADMIN_PASSWORD and restart.");
            return;
        }
        PasswordPolicy.check(password);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Seed admin email {} already belongs to another user - not seeding", email);
            return;
        }
        User u = new User();
        u.setUid(Ulids.next());
        u.setUserType(UserType.ADMIN);
        u.setRole(superAdmin);
        u.setName("Super Admin");
        u.setEmail(email.trim().toLowerCase());
        u.setMobile(seedMobile);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePassword(!props.isDevMode());
        userRepository.save(u);
        log.info("Super admin created (mobile {})", in.codebasket.cbwa.common.util.PhoneUtil.mask(seedMobile));
    }
}
