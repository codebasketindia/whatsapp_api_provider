package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.constants.UserStatus;
import in.codebasket.cbwa.common.constants.UserType;
import in.codebasket.cbwa.common.entity.User;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUid(String uid);

    Optional<User> findByMobile(String mobile);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleId(Long roleId);

    List<User> findByUserTypeAndStatus(UserType userType, UserStatus status);
}
