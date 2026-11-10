package in.codebasket.cbwa.common.controller;

import in.codebasket.cbwa.common.entity.RegistrationForm;
import in.codebasket.cbwa.common.repository.PartnerRepository;
import in.codebasket.cbwa.common.repository.RegistrationFormRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Link preview for a booking link. Link-preview bots (WhatsApp, Facebook, Telegram...) do not run JavaScript, so
 * the web server sends them here for /r/{slug}: a small HTML page with Open Graph tags (title, description, image).
 * A person who opens this URL is sent on to the booking page.
 */
@RestController
@RequestMapping("/api/public/share")
@RequiredArgsConstructor
@Tag(name = "Public")
public class SharePreviewController {

    private final RegistrationFormRepository forms;
    private final PartnerRepository partners;

    @GetMapping(value = "/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@PathVariable String slug, HttpServletRequest request) {
        RegistrationForm f = forms.findBySlug(slug).orElse(null);
        if (f == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body("<!doctype html><title>Not found</title>");
        }
        String origin = origin(request);
        String business = partners.findById(f.getPartnerId()).map(p -> p.getBusinessName()).orElse(null);
        String title = first(f.getShareTitle(), f.getTitle());
        String description = first(f.getShareDescription(), business, f.getTitle());
        String image = absolute(origin, first(f.getShareImageUrl(), f.getHeroImageUrl()));
        String url = origin + "/r/" + f.getSlug();

        StringBuilder h = new StringBuilder(1200);
        h.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc(title)).append("</title>")
                .append(meta("name", "description", description))
                .append("<link rel=\"canonical\" href=\"").append(esc(url)).append("\">")
                .append(meta("property", "og:type", "website"))
                .append(meta("property", "og:site_name", title))
                .append(meta("property", "og:title", title))
                .append(meta("property", "og:description", description))
                .append(meta("property", "og:url", url));
        if (image != null) {
            h.append(meta("property", "og:image", image))
                    .append(meta("property", "og:image:secure_url", image))
                    .append(meta("property", "og:image:alt", title));
            if (image.endsWith("/ziyarat/share-hajj-2027.jpg")) {
                h.append(meta("property", "og:image:type", "image/jpeg"))
                        .append(meta("property", "og:image:width", "1200"))
                        .append(meta("property", "og:image:height", "630"));
            }
            h.append(meta("name", "twitter:card", "summary_large_image")).append(meta("name", "twitter:image", image));
        }
        h.append(meta("name", "twitter:title", title)).append(meta("name", "twitter:description", description))
                .append("</head><body><p><a href=\"").append(esc(url)).append("\">").append(esc(title)).append("</a></p>")
                .append("<script>location.replace(").append(jsString(url)).append(")</script></body></html>");
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache()).body(h.toString());
    }

    /** Origin of the site the link was shared from (the proxy's host), so previews work on every domain. */
    private static String origin(HttpServletRequest r) {
        String proto = header(r, "X-Forwarded-Proto", r.getScheme());
        String host = header(r, "X-Forwarded-Host", header(r, "Host", r.getServerName()));
        return proto.split(",")[0].trim() + "://" + host.split(",")[0].trim();
    }

    private static String header(HttpServletRequest r, String name, String fallback) {
        String v = r.getHeader(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String absolute(String origin, String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("https://") || url.startsWith("http://")) {
            return url;
        }
        return url.startsWith("/") && !url.startsWith("//") ? origin + url : null;
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String meta(String attr, String key, String content) {
        return "<meta " + attr + "=\"" + key + "\" content=\"" + esc(content) + "\">";
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\"";
    }
}
