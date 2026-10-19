package in.codebasket.cbwa.common.storage;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * Stores uploads on local disk outside the web root with random names. The real content type is
 * detected from the bytes (magic numbers) - the client supplied type/extension is never trusted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    public static final Set<String> IMAGES = Set.of("image/jpeg", "image/png");
    public static final Set<String> IMAGES_PDF = Set.of("image/jpeg", "image/png", "application/pdf");
    public static final Set<String> SPREADSHEETS = Set.of("text/csv", "text/plain",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/x-tika-ooxml",
            "application/vnd.ms-excel", "application/x-tika-msoffice");

    private static final Map<String, String> EXT = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"), Map.entry("image/png", "png"), Map.entry("image/webp", "webp"),
            Map.entry("image/svg+xml", "svg"), Map.entry("application/pdf", "pdf"), Map.entry("text/csv", "csv"),
            Map.entry("text/plain", "txt"), Map.entry("video/mp4", "mp4"), Map.entry("video/3gpp", "3gp"),
            Map.entry("audio/mpeg", "mp3"), Map.entry("audio/ogg", "ogg"), Map.entry("audio/aac", "aac"),
            Map.entry("audio/amr", "amr"), Map.entry("audio/mp4", "m4a"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/vnd.ms-excel", "xls"), Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"));

    private final AppProperties props;
    private final StoredFileRepository repository;
    private final Tika tika = new Tika();

    public StoredFile store(MultipartFile file, FileCategory category, Long partnerId, Set<String> allowedTypes, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw AppException.field("file", "Please choose a file");
        }
        if (file.getSize() > maxBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "File is larger than " + (maxBytes / (1024 * 1024)) + " MB");
        }
        try {
            return store(file.getBytes(), file.getOriginalFilename(), category, partnerId, allowedTypes, maxBytes);
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Could not read the uploaded file");
        }
    }

    public StoredFile store(byte[] bytes, String originalName, FileCategory category, Long partnerId,
                            Set<String> allowedTypes, long maxBytes) {
        if (bytes.length > maxBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "File is larger than " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String safeName = sanitize(originalName);
        String mime = detect(bytes, safeName);
        if (allowedTypes != null && !allowedTypes.isEmpty() && !allowedTypes.contains(mime)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "This file type (" + mime + ") is not allowed");
        }
        // Normalise Tika container types for office files to their real MIME types
        if (mime.startsWith("application/x-tika-ooxml")) {
            mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (mime.startsWith("application/x-tika-msoffice")) {
            mime = "application/vnd.ms-excel";
        }
        String uid = Ulids.next();
        String ext = EXT.getOrDefault(mime, "bin");
        String month = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC).format(Instant.now());
        Path relative = Paths.get(category.name().toLowerCase(), month, uid + "." + ext);
        Path target = root().resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("Failed to store file {}", target, e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Could not save the file");
        }
        StoredFile sf = new StoredFile();
        sf.setUid(uid);
        sf.setPartnerId(partnerId);
        sf.setCategory(category);
        sf.setOriginalName(safeName);
        sf.setStoragePath(relative.toString().replace('\\', '/'));
        sf.setMimeType(mime);
        sf.setSizeBytes(bytes.length);
        sf.setSha256(HashUtil.sha256Hex(bytes));
        sf.setCreatedBy(SecurityUtils.currentUserIdOrNull());
        sf.setCreatedAt(Instant.now());
        return repository.save(sf);
    }

    public StoredFile get(String uid) {
        return repository.findByUid(uid).orElseThrow(() -> AppException.notFound("File"));
    }

    public StoredFile get(Long id) {
        return repository.findById(id).orElseThrow(() -> AppException.notFound("File"));
    }

    public Resource load(StoredFile file) {
        Path path = root().resolve(file.getStoragePath()).normalize();
        if (!path.startsWith(root()) || !Files.exists(path)) {
            throw AppException.notFound("File");
        }
        return new FileSystemResource(path);
    }

    public byte[] readBytes(StoredFile file) {
        try {
            return Files.readAllBytes(root().resolve(file.getStoragePath()).normalize());
        } catch (IOException e) {
            throw AppException.notFound("File");
        }
    }

    public void delete(StoredFile file) {
        try {
            Files.deleteIfExists(root().resolve(file.getStoragePath()).normalize());
        } catch (IOException e) {
            log.warn("Could not delete file {}", file.getUid());
        }
        repository.delete(file);
    }

    public String detect(byte[] bytes, String name) {
        String mime = tika.detect(bytes, name);
        if ("text/plain".equals(mime) && name != null && name.toLowerCase().endsWith(".csv")) {
            return "text/csv";
        }
        return mime;
    }

    private Path root() {
        return Paths.get(props.getStorage().getPath()).toAbsolutePath().normalize();
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = Paths.get(name).getFileName().toString().replaceAll("[^A-Za-z0-9._ ()-]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
