package in.codebasket.cbwa.common.storage;

import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** A16: authorised file download. Admins may read any file; partners only their own. */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService storage;

    @GetMapping("/{uid}")
    public ResponseEntity<Resource> download(@PathVariable String uid,
                                             @RequestParam(defaultValue = "false") boolean download) {
        StoredFile file = storage.get(uid);
        AuthPrincipal p = SecurityUtils.current();
        boolean allowed = p.isAdmin()
                || (file.getPartnerId() != null && file.getPartnerId().equals(p.partnerId()))
                || file.getCategory() == FileCategory.COMPANY_LOGO;
        if (!allowed) {
            throw AppException.notFound("File");
        }
        boolean inline = !download && (file.getMimeType().startsWith("image/") || file.getMimeType().equals("application/pdf")
                || file.getMimeType().startsWith("video/") || file.getMimeType().startsWith("audio/"));
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(storage.load(file));
    }
}
