package in.codebasket.cbwa.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    Optional<StoredFile> findByUid(String uid);
}
