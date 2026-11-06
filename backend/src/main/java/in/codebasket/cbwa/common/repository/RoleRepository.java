package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.Role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);

    List<Role> findAllByOrderByIdAsc();
}
