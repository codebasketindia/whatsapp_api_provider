package in.codebasket.cbwa.convtools;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrCodeRepository extends JpaRepository<QrCode, Long> {
    List<QrCode> findByPhoneNumberIdOrderByIdDesc(Long phoneNumberId);

    Optional<QrCode> findByIdAndPhoneNumberId(Long id, Long phoneNumberId);
}
