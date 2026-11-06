package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.StoredFile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    Optional<StoredFile> findByUid(String uid);
}
