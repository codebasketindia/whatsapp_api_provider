package in.codebasket.cbwa.common.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 26)
    private String uid;

    private Long partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileCategory category;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private String mimeType;

    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt;
}
