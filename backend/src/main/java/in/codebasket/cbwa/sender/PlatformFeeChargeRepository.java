package in.codebasket.cbwa.sender;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformFeeChargeRepository extends JpaRepository<PlatformFeeCharge, Long> {
    Optional<PlatformFeeCharge> findByPhoneNumberIdAndPeriod(Long phoneNumberId, String period);

    List<PlatformFeeCharge> findByStatus(String status);
}
