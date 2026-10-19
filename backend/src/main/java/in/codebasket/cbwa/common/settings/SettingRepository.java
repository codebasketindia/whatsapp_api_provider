package in.codebasket.cbwa.common.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettingRepository extends JpaRepository<Setting, Long> {
    List<Setting> findByGroupCodeOrderById(String groupCode);

    Optional<Setting> findByGroupCodeAndSettingKey(String groupCode, String settingKey);
}
