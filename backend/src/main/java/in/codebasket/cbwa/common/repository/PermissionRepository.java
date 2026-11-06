package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.Permission;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
}
