package in.codebasket.cbwa.meta;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * The only class that talks to the Meta Graph API (F: "only the backend ever calls Meta").
 * Base URL: {cbwa.meta.graph-base-url}/{graph version from settings}. Tokens are passed per call
 * and never logged. Errors become MetaApiException with Meta's code/subcode.
 */
@Slf4j
@Component
public class MetaGraphClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final MetaSettingsService metaSettings;
    private final AppProperties props;

    public MetaGraphClient(WebClient.Builder builder, MetaSettingsService metaSettings, AppProperties props) {
        this.webClient = builder.clone().build();
        this.metaSettings = metaSettings;
        this.props = props;
    }

    public JsonNode get(String path, Map<String, ?> query, String token) {
        return exchange(HttpMethod.GET, path, query, null, token);
    }

    public JsonNode post(String path, Object jsonBody, String token) {
        return exchange(HttpMethod.POST, path, null, jsonBody, token);
    }

    /** POST with parameters in the query string (some Graph endpoints take no JSON body). */
    public JsonNode postQuery(String path, Map<String, ?> query, String token) {
        return exchange(HttpMethod.POST, path, query, null, token);
    }

    public JsonNode delete(String path, Map<String, ?> query, String token) {
        return exchange(HttpMethod.DELETE, path, query, null, token);
    }

    /** POST /{PHONE_NUMBER_ID}/media (multipart) -> media id. */
    public String uploadMedia(String phoneNumberId, byte[] bytes, String mimeType, String filename, String token) {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("messaging_product", "whatsapp");
        mb.part("type", mimeType);
        mb.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType(mimeType));
        try {
            String body = webClient.post().uri(uri("/" + phoneNumberId + "/media", null))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(mb.build()))
                    .retrieve().bodyToMono(String.class).block(TIMEOUT);
            return JsonUtil.readTree(body).path("id").asText();
        } catch (WebClientResponseException e) {
            throw toMetaException(e);
        } catch (WebClientRequestException e) {
            throw network(e);
        }
    }

    /** Downloads media from a Meta media URL (requires the same bearer token). */
    public byte[] download(String url, String token) {
        try {
            return webClient.get().uri(URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().bodyToMono(byte[].class).block(Duration.ofSeconds(60));
        } catch (WebClientResponseException e) {
            throw toMetaException(e);
        } catch (WebClientRequestException e) {
            throw network(e);
        }
    }

    /**
     * Resumable Upload API: creates an upload session on the app and uploads the bytes in one go.
     * Returns the file handle ("h") used for template header examples and profile pictures.
     */
    public String resumableUpload(byte[] bytes, String mimeType, String filename, String token) {
        String appId = metaSettings.appId();
        JsonNode session = postQuery("/" + appId + "/uploads",
                Map.of("file_name", filename, "file_length", bytes.length, "file_type", mimeType), token);
        String sessionId = session.path("id").asText();
        try {
            String body = webClient.post().uri(uri("/" + sessionId, null))
                    .header(HttpHeaders.AUTHORIZATION, "OAuth " + token)
                    .header("file_offset", "0")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(bytes)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(60));
            return JsonUtil.readTree(body).path("h").asText();
        } catch (WebClientResponseException e) {
            throw toMetaException(e);
        } catch (WebClientRequestException e) {
            throw network(e);
        }
    }

    /** Embedded Signup: exchange the 30-second code for a business integration token. */
    public String exchangeCode(String code) {
        JsonNode res = get("/oauth/access_token", Map.of("client_id", metaSettings.appId(),
                "client_secret", metaSettings.appSecret(), "code", code), null);
        return res.path("access_token").asText(null);
    }

    /** GET /debug_token with the app token: validity, expiry and scopes of a user/system token. */
    public JsonNode debugToken(String inputToken) {
        return get("/debug_token", Map.of("input_token", inputToken), metaSettings.appAccessToken()).path("data");
    }

    private JsonNode exchange(HttpMethod method, String path, Map<String, ?> query, Object jsonBody, String token) {
        try {
            WebClient.RequestBodySpec spec = webClient.method(method).uri(uri(path, query));
            if (token != null) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            WebClient.RequestHeadersSpec<?> req = jsonBody == null ? spec
                    : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(jsonBody instanceof String s ? s : JsonUtil.toJson(jsonBody));
            String body = req.retrieve().bodyToMono(String.class).block(TIMEOUT);
            return JsonUtil.readTree(body);
        } catch (WebClientResponseException e) {
            throw toMetaException(e);
        } catch (WebClientRequestException e) {
            throw network(e);
        } catch (IllegalStateException e) {
            // block() timeout
            throw new MetaApiException(0, null, null, "Timed out talking to WhatsApp", null, null);
        }
    }

    private Function<UriBuilder, URI> uri(String path, Map<String, ?> query) {
        String base = props.getMeta().getGraphBaseUrl() + "/" + metaSettings.graphVersion();
        return b -> {
            URI baseUri = URI.create(base + (path.startsWith("/") ? path : "/" + path));
            UriBuilder ub = b.scheme(baseUri.getScheme()).host(baseUri.getHost()).port(baseUri.getPort()).path(baseUri.getPath());
            if (query != null) {
                query.forEach((k, v) -> {
                    if (v != null) {
                        ub.queryParam(k, "{" + k + "}");
                    }
                });
                Map<String, Object> vars = new java.util.HashMap<>();
                query.forEach((k, v) -> {
                    if (v != null) {
                        vars.put(k, String.valueOf(v));
                    }
                });
                return ub.build(vars);
            }
            return ub.build();
        };
    }

    static MetaApiException toMetaException(WebClientResponseException e) {
        JsonNode err;
        try {
            err = JsonUtil.readTree(e.getResponseBodyAsString()).path("error");
        } catch (RuntimeException ex) {
            err = JsonUtil.MAPPER.createObjectNode();
        }
        Integer code = err.hasNonNull("code") ? err.get("code").asInt() : null;
        Integer subcode = err.hasNonNull("error_subcode") ? err.get("error_subcode").asInt() : null;
        String message = err.path("error_user_msg").asText(err.path("message").asText(e.getStatusText()));
        String details = err.path("error_data").path("details").asText(null);
        MetaApiException ex = new MetaApiException(e.getStatusCode().value(), code, subcode, message, details,
                err.path("fbtrace_id").asText(null));
        log.warn("Meta API error http={} code={} subcode={} msg={} trace={}", ex.getHttpStatus(), code, subcode, message, ex.getFbtraceId());
        return ex;
    }

    private static MetaApiException network(WebClientRequestException e) {
        log.warn("Meta API network error: {}", e.getMessage());
        return new MetaApiException(0, null, null, "Could not reach WhatsApp servers", null, null);
    }
}
