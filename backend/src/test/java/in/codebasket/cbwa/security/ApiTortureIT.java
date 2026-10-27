package in.codebasket.cbwa.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.user.UserMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Third degree" API torture test. Every route registered in Spring MVC is called by every role
 * with real ids, ids of another tenant, non-existent and hostile ids, and with empty / malformed /
 * wrongly typed / oversized / hostile bodies and query parameters derived from the controller method
 * signature. Invariants:
 * <ul>
 *   <li>no 5xx and no stack trace for any input;</li>
 *   <li>every error is the standard JSON envelope;</li>
 *   <li>anonymous -> 401 on protected routes; partner -> 403 on admin routes; admin -> 403 on partner routes;</li>
 *   <li>staff lacking the route's permission never gets 2xx;</li>
 *   <li>partner B never gets 2xx (nor sees partner A's data) when addressing partner A's resources;</li>
 *   <li>no planted secret (app secret, tokens, PIN, key/webhook secrets, passwords) ever appears in a response.</li>
 * </ul>
 * A report of every request is written to target/api-torture-report.txt.
 */
class ApiTortureIT extends AbstractIntegrationTest {

    static final WireMockServer EXT = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        EXT.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.meta.graph-base-url", () -> "http://localhost:" + EXT.port());
        r.add("cbwa.razorpay.base-url", () -> "http://localhost:" + EXT.port());
        r.add("cbwa.webhook.async", () -> "false");
    }

    @AfterAll
    static void stop() {
        EXT.stop();
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Autowired
    TortureFixtures fixtures;

    enum Role { ANON, API_KEY_A, PARTNER_A, PARTNER_B, SUPPORT, FINANCE, SUPER }

    record Route(String method, String pattern, HandlerMethod handler) {
        String key() {
            return method + " " + pattern;
        }
    }

    record Failure(Role role, String route, String variant, int status, String body) {
        @Override
        public String toString() {
            return role + " | " + route + " | " + variant + " -> " + status + " | " + body;
        }
    }

    static final String HUGE = "A".repeat(10_050);
    static final String HOSTILE = "' OR 1=1 -- <script>alert(1)</script> ../../etc/passwd 😀 \u0000x";
    static final String[] FOREIGN_IDS = {"999999", "abc", "../../etc/passwd", "1 OR 1=1", "-1", "99999999999999999999"};

    final List<Failure> failures = new ArrayList<>();
    final List<String> report = new ArrayList<>();
    int requests;

    @Test
    void everyRouteSurvivesEveryRoleAndHostileInput() throws Exception {
        EXT.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"123\",\"data\":[],\"success\":true,\"messages\":[{\"id\":\"wamid.X\"}],\"paging\":{}}")));
        List<Route> routes = routes();
        assertThat(routes).hasSizeGreaterThan(250);

        for (Role role : Role.values()) {
            // fresh tenants per role so earlier destructive calls never hide later success paths
            TortureFixtures.World w = fixtures.build();
            for (Route route : routes) {
                torture(w, role, route);
            }
        }

        Path out = Path.of("target", "api-torture-report.txt");
        Files.createDirectories(out.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("routes=" + routes.size() + " roles=" + Role.values().length + " requests=" + requests + " failures=" + failures.size());
        failures.forEach(f -> lines.add("FAIL " + f));
        lines.addAll(report);
        Files.write(out, lines, StandardCharsets.UTF_8);
        assertThat(failures).as("API torture failures (see target/api-torture-report.txt)\n" + summary()).isEmpty();
    }

    private String summary() {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new TreeSet<>();
        for (Failure f : failures) {
            String k = f.role() + " " + f.route() + " -> " + f.status() + " " + f.body().substring(0, Math.min(180, f.body().length()));
            if (seen.add(k) && seen.size() <= 150) {
                sb.append(k).append(" [").append(f.variant()).append("]\n");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ route discovery

    private List<Route> routes() {
        List<Route> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            Set<String> patterns = e.getKey().getPatternValues();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = e.getKey().getMethodsCondition().getMethods();
            for (String p : patterns) {
                if (!p.startsWith("/api/")) {
                    continue;
                }
                for (var m : methods) {
                    out.add(new Route(m.name(), p, e.getValue()));
                }
            }
        }
        // reads first, deletes last, so a route's own fixture still exists when it is read or changed
        List<String> order = List.of("GET", "POST", "PUT", "PATCH", "DELETE");
        out.sort(java.util.Comparator.comparingInt((Route r) -> order.indexOf(r.method())).thenComparing(Route::pattern));
        return out;
    }

    // ------------------------------------------------------------------ one route x one role

    private void torture(TortureFixtures.World w, Role role, Route route) throws Exception {
        TortureFixtures.Tenant owner = w.a;
        boolean protectedRoute = !isPublic(route.pattern());
        if (role == Role.ANON) {
            send(w, role, route, ids(route, owner, null), "{}", null, "anon-real-ids");
            send(w, role, route, ids(route, owner, null), null, null, "anon-no-body");
            return;
        }
        // real ids (of partner A) with every body variant
        Map<String, String> real = ids(route, owner, w);
        for (Map.Entry<String, String> body : bodies(route).entrySet()) {
            send(w, role, route, real, body.getValue(), null, "real-ids/" + body.getKey());
        }
        send(w, role, route, real, plausible(route), hostileQuery(route, false), "real-ids/hostile-query");
        send(w, role, route, real, plausible(route), hostileQuery(route, true), "real-ids/edge-query");
        if (!route.pattern().contains("{")) {
            return;
        }
        for (String foreign : FOREIGN_IDS) {
            Map<String, String> vars = new LinkedHashMap<>();
            for (String v : vars(route.pattern())) {
                vars.put(v, foreign);
            }
            send(w, role, route, vars, "{}", null, "foreign-id=" + foreign);
        }
        if (!protectedRoute) {
            return;
        }
    }

    private void send(TortureFixtures.World w, Role role, Route route, Map<String, String> vars, String body,
                      Map<String, String> query, String variant) throws Exception {
        HttpMethod method = HttpMethod.valueOf(route.method());
        Object[] varValues = vars(route.pattern()).stream().map(v -> vars.getOrDefault(v, "1")).toArray();
        MultipartFile[] none = {};
        MockHttpServletRequestBuilder b;
        List<String> fileParts = fileParams(route);
        if (!fileParts.isEmpty() && body != null && body.startsWith("MULTIPART:")) {
            MockMultipartHttpServletRequestBuilder mb = MockMvcRequestBuilders.multipart(method, route.pattern(), varValues);
            String kind = body.substring("MULTIPART:".length());
            for (String part : fileParts) {
                MockMultipartFile f = badFile(part, kind);
                if (f != null) {
                    mb.file(f);
                }
            }
            for (Map.Entry<String, String> qp : multipartFields(route).entrySet()) {
                mb.param(qp.getKey(), qp.getValue());
            }
            b = mb;
        } else {
            b = MockMvcRequestBuilders.request(method, route.pattern(), varValues);
            if (body != null && !body.startsWith("MULTIPART:")) {
                b.contentType(MediaType.APPLICATION_JSON).content(body);
            }
        }
        if (query != null) {
            query.forEach(b::param);
        }
        auth(w, role, b);
        requests++;
        int status;
        String resp;
        try {
            MvcResult r = mockMvc.perform(b).andReturn();
            if (r.getRequest().isAsyncStarted()) {
                r.getAsyncResult(2000);
            }
            status = r.getResponse().getStatus();
            String ct = r.getResponse().getContentType();
            resp = ct != null && (ct.contains("json") || ct.contains("text")) ? r.getResponse().getContentAsString(StandardCharsets.UTF_8) : "<" + ct + ">";
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            fail(role, route, variant, 599, "EXCEPTION " + root.getClass().getSimpleName() + ": " + root.getMessage());
            return;
        }
        check(w, role, route, vars, variant, status, resp);
    }

    // ------------------------------------------------------------------ invariants

    private void check(TortureFixtures.World w, Role role, Route route, Map<String, String> vars, String variant, int status, String body) {
        String p = route.pattern();
        boolean ok2xx = status >= 200 && status < 300;
        // Contracted upstream/config statuses: 502 META_API_ERROR (Meta failed), 503 *_NOT_CONFIGURED / AI_NOT_AVAILABLE.
        boolean contracted = (status == 502 && body.contains("\"META_API_ERROR\""))
                || (status == 503 && body.matches("(?s).*\"code\":\"([A-Z_]+_NOT_CONFIGURED|AI_NOT_AVAILABLE)\".*"));
        if (status >= 500 && !contracted) {
            fail(role, route, variant, status, body);
        }
        if (status == 400 && body.contains("This request URL is not allowed")) {
            // rejected by the HTTP firewall before authentication - a safe deny for every role
            report.add(role + " " + route.key() + " [" + variant + "] -> firewall 400");
            return;
        }
        if (body.contains("at in.codebasket") || body.contains("Exception:") || body.contains("org.springframework.") || body.contains("java.lang.")) {
            fail(role, route, variant, status, "STACK/INTERNAL DETAIL LEAK " + body);
        }
        if (status >= 400 && !body.startsWith("<")) {
            JsonNode n;
            try {
                n = JsonUtil.readTree(body);
            } catch (RuntimeException e) {
                n = null;
            }
            boolean envelope = n != null && n.has("success") && !n.path("success").asBoolean(true) && n.hasNonNull("code");
            if (!envelope && !p.startsWith("/api/webhooks/whatsapp")) {
                fail(role, route, variant, status, "NOT AN ERROR ENVELOPE " + body);
            }
        }
        for (String secret : w.secrets) {
            if (body.contains(secret)) {
                fail(role, route, variant, status, "SECRET LEAK (" + secret + ")");
            }
        }
        if (body.contains("$2a$") || body.contains("passwordHash") || body.contains("secretHash") || body.contains("accessTokenEnc")
                || body.contains("pinEnc") || body.contains("v1:")) {
            fail(role, route, variant, status, "HASH/ENCRYPTED FIELD LEAK");
        }
        boolean isPublic = isPublic(p);
        switch (role) {
            case ANON, API_KEY_A -> {
                boolean v1 = p.startsWith("/api/v1/public/");
                boolean allowed = isPublic || (role == Role.API_KEY_A && v1);
                if (!allowed && status != 401) {
                    fail(role, route, variant, status, "EXPECTED 401 " + body);
                }
            }
            case PARTNER_A, PARTNER_B -> {
                if ((p.startsWith("/api/admin/") && status != 403) || (p.startsWith("/api/v1/public/") && status != 401)) {
                    fail(role, route, variant, status, "EXPECTED DENY " + body);
                }
            }
            case SUPPORT, FINANCE, SUPER -> {
                if ((p.startsWith("/api/partner/") && status != 403) || (p.startsWith("/api/v1/public/") && status != 401)) {
                    fail(role, route, variant, status, "EXPECTED DENY " + body);
                }
                String perm = requiredPermission(route);
                if (perm != null && role != Role.SUPER && !hasPerm(w, role, perm) && ok2xx) {
                    fail(role, route, variant, status, "MISSING PERMISSION " + perm + " BUT 2xx");
                }
            }
        }
        // cross-tenant: partner B addressing partner A's resources must never succeed or see A's data
        if (role == Role.PARTNER_B && variant.startsWith("real-ids") && p.startsWith("/api/partner/") && p.contains("{")) {
            if (ok2xx && !crossTenantExempt(route)) {
                fail(role, route, variant, status, "CROSS-TENANT 2xx " + body);
            }
        }
        if (role == Role.PARTNER_B || role == Role.API_KEY_A && false) {
            for (String marker : w.a.markers) {
                if (body.contains(marker)) {
                    fail(role, route, variant, status, "CROSS-TENANT DATA (" + marker + ")");
                }
            }
        }
        report.add(role + " " + route.key() + " [" + variant + "] -> " + status);
    }

    /** Routes whose path variables never identify another partner's resource. */
    private boolean crossTenantExempt(Route r) {
        return r.pattern().startsWith("/api/partner/template-library") || r.pattern().contains("/developer/");
    }

    private void fail(Role role, Route route, String variant, int status, String body) {
        failures.add(new Failure(role, route.key(), variant, status, body == null ? "" : body.substring(0, Math.min(400, body.length()))));
    }

    private boolean isPublic(String p) {
        return (p.startsWith("/api/auth/") && !p.equals("/api/auth/logout")) || p.startsWith("/api/webhooks/") || p.startsWith("/api/public/");
    }

    private boolean hasPerm(TortureFixtures.World w, Role role, String perm) {
        var user = role == Role.SUPPORT ? w.support : w.finance;
        return UserMapper.permissionsOf(user).contains(perm);
    }

    private static final Pattern PERM = Pattern.compile("PERM_([A-Z_]+)");

    private String requiredPermission(Route r) {
        PreAuthorize pa = r.handler().getMethodAnnotation(PreAuthorize.class);
        if (pa == null) {
            pa = r.handler().getBeanType().getAnnotation(PreAuthorize.class);
        }
        if (pa != null) {
            Matcher m = PERM.matcher(pa.value());
            return m.find() ? m.group(1) : null;
        }
        if (r.handler().getBeanType().getSimpleName().equals("SenderToolsController") && r.pattern().startsWith("/api/admin/")) {
            return r.method().equals("GET") ? "WHATSAPP_VIEW" : "WHATSAPP_MANAGE";
        }
        return null;
    }

    private void auth(TortureFixtures.World w, Role role, MockHttpServletRequestBuilder b) {
        switch (role) {
            case ANON -> { }
            case API_KEY_A -> b.header("X-API-KEY", w.a.apiKey).header("X-API-SECRET", TortureFixtures.API_SECRET);
            case PARTNER_A -> b.header("Authorization", "Bearer " + w.a.token);
            case PARTNER_B -> b.header("Authorization", "Bearer " + w.b.token);
            case SUPPORT -> b.header("Authorization", "Bearer " + w.supportToken);
            case FINANCE -> b.header("Authorization", "Bearer " + w.financeToken);
            case SUPER -> b.header("Authorization", "Bearer " + w.superToken);
        }
    }

    // ------------------------------------------------------------------ ids

    private static final Pattern VAR = Pattern.compile("\\{([^}/:]+)(?::[^}]*)?}");

    private static List<String> vars(String pattern) {
        List<String> out = new ArrayList<>();
        Matcher m = VAR.matcher(pattern);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private Map<String, String> ids(Route r, TortureFixtures.Tenant t, TortureFixtures.World w) {
        Map<String, String> out = new LinkedHashMap<>();
        String p = r.pattern();
        for (String v : vars(p)) {
            out.put(v, resolve(p, v, t, w));
        }
        return out;
    }

    private String resolve(String p, String v, TortureFixtures.Tenant t, TortureFixtures.World w) {
        Map<String, String> ids = t.ids;
        if (v.equals("waId")) {
            return ids.get("waId");
        }
        if (v.equals("senderId")) {
            return ids.get("sender");
        }
        if (v.equals("qrId")) {
            return ids.get("qr");
        }
        if (v.equals("keyId")) {
            return ids.get("apiKey");
        }
        if (v.equals("group")) {
            return "company";
        }
        if (p.startsWith("/api/files/")) {
            return ids.get("file");
        }
        if (p.contains("/senders/{id}")) {
            return ids.get("sender");
        }
        if (p.contains("/templates/{id}")) {
            return ids.get("template");
        }
        if (p.contains("/messages/{")) {
            return ids.get("message");
        }
        if (p.contains("/campaigns/{id}")) {
            return ids.get("campaign");
        }
        if (p.contains("/contact-groups/{id}")) {
            return ids.get("group");
        }
        if (p.contains("/contacts/{id}")) {
            return ids.get("contact");
        }
        if (p.contains("/api-keys/{id}")) {
            return ids.get("apiKey");
        }
        if (p.contains("/webhook/logs/{id}")) {
            return ids.get("deliveryLog");
        }
        if (p.contains("/payments/") || p.contains("/payments/orders/")) {
            return ids.get("payment");
        }
        if (p.contains("/invoices/{id}")) {
            return ids.get("invoice");
        }
        if (p.contains("/topup-requests/{id}")) {
            return ids.get("topup");
        }
        if (p.contains("/partners/{id}")) {
            return ids.get("partner");
        }
        if (p.contains("/accounts/{id}")) {
            return ids.get("waba");
        }
        if (p.contains("/staff/{uid}") && w != null) {
            return w.support.getUid();
        }
        if (p.contains("/roles/{id}")) {
            return "3";
        }
        if (p.contains("/library/{name}")) {
            return "order_shipped";
        }
        return "1";
    }

    // ------------------------------------------------------------------ bodies & params

    private Class<?> bodyType(Route r) {
        for (MethodParameter mp : r.handler().getMethodParameters()) {
            if (mp.hasParameterAnnotation(RequestBody.class)) {
                return mp.getParameterType();
            }
        }
        return null;
    }

    private List<String> fileParams(Route r) {
        List<String> out = new ArrayList<>();
        for (MethodParameter mp : r.handler().getMethodParameters()) {
            if (MultipartFile.class.isAssignableFrom(mp.getParameterType())) {
                RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
                RequestPart part = mp.getParameterAnnotation(RequestPart.class);
                String name = rp != null && !rp.value().isEmpty() ? rp.value() : rp != null && !rp.name().isEmpty() ? rp.name()
                        : part != null && !part.value().isEmpty() ? part.value() : part != null ? part.name() : pname(mp);
                out.add(name == null || name.isEmpty() ? "file" : name);
            }
        }
        return out;
    }

    private Map<String, String> multipartFields(Route r) {
        Map<String, String> out = new LinkedHashMap<>();
        for (MethodParameter mp : r.handler().getMethodParameters()) {
            RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
            RequestPart part = mp.getParameterAnnotation(RequestPart.class);
            if ((rp == null && part == null) || MultipartFile.class.isAssignableFrom(mp.getParameterType())) {
                continue;
            }
            String name = rp != null ? (rp.value().isEmpty() ? rp.name() : rp.value()) : (part.value().isEmpty() ? part.name() : part.value());
            if (name == null || name.isEmpty()) {
                name = pname(mp);
            }
            if (!name.isEmpty()) {
                out.put(name, sample(mp.getParameterType(), true));
            }
        }
        return out;
    }

    private Map<String, String> bodies(Route r) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("no-body", null);
        if (!fileParams(r).isEmpty()) {
            for (String kind : List.of("zero", "fake-png", "svg-script", "big", "polyglot", "traversal-name")) {
                out.put("multipart-" + kind, "MULTIPART:" + kind);
            }
        }
        Class<?> type = bodyType(r);
        if (type == null && !r.method().equals("GET") && !r.method().equals("DELETE")) {
            out.put("unexpected-json", "{\"x\":1}");
            return out;
        }
        if (type == null) {
            return out;
        }
        out.put("empty-object", "{}");
        out.put("malformed", "{\"a\": ");
        out.put("array", "[1,2,3]");
        out.put("string", "\"hello\"");
        out.put("null", "null");
        out.put("wrong-types", JsonUtil.toJson(fuzz(type, Mode.WRONG_TYPES, 0)));
        out.put("hostile-big", JsonUtil.toJson(fuzz(type, Mode.HOSTILE_BIG, 0)));
        out.put("hostile-plausible", JsonUtil.toJson(fuzz(type, Mode.PLAUSIBLE, 0)));
        out.put("extreme-numbers", JsonUtil.toJson(fuzz(type, Mode.EXTREME, 0)));
        return out;
    }

    private String plausible(Route r) {
        Class<?> type = bodyType(r);
        return type == null ? null : JsonUtil.toJson(fuzz(type, Mode.PLAUSIBLE, 0));
    }

    enum Mode { WRONG_TYPES, HOSTILE_BIG, PLAUSIBLE, EXTREME }

    private Object fuzz(Type type, Mode mode, int depth) {
        Class<?> c = raw(type);
        if (depth > 3) {
            return null;
        }
        if (c == String.class) {
            return switch (mode) {
                case WRONG_TYPES -> List.of("x");
                case HOSTILE_BIG -> HUGE;
                case PLAUSIBLE -> HOSTILE;
                case EXTREME -> "";
            };
        }
        if (Number.class.isAssignableFrom(c) || c == int.class || c == long.class || c == double.class) {
            return switch (mode) {
                case WRONG_TYPES -> "abc";
                case HOSTILE_BIG -> new BigDecimal("1E+18");
                case PLAUSIBLE -> c == BigDecimal.class ? new BigDecimal("12.123456") : 999999;
                case EXTREME -> c == BigDecimal.class ? new BigDecimal("-0.0001") : -1;
            };
        }
        if (c == Boolean.class || c == boolean.class) {
            return mode == Mode.WRONG_TYPES ? "maybe" : Boolean.TRUE;
        }
        if (c == LocalDate.class || c == Instant.class) {
            return mode == Mode.WRONG_TYPES ? 5 : mode == Mode.EXTREME ? "1900-01-01" : "2026-13-45";
        }
        if (c.isEnum()) {
            return "NOPE";
        }
        if (Collection.class.isAssignableFrom(c)) {
            if (mode == Mode.WRONG_TYPES) {
                return "notalist";
            }
            Type elem = type instanceof ParameterizedType pt ? pt.getActualTypeArguments()[0] : String.class;
            List<Object> list = new ArrayList<>();
            int n = mode == Mode.HOSTILE_BIG ? 1200 : 2;
            // big lists use short elements so a single body stays a few hundred KB
            Mode elemMode = mode == Mode.HOSTILE_BIG ? Mode.EXTREME : mode;
            Object first = fuzz(elem, elemMode, depth + 1);
            for (int i = 0; i < n; i++) {
                list.add(first);
            }
            return list;
        }
        if (Map.class.isAssignableFrom(c)) {
            if (mode == Mode.WRONG_TYPES) {
                return List.of(1);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(mode == Mode.HOSTILE_BIG ? HUGE.substring(0, 300) : "<script>", mode == Mode.HOSTILE_BIG ? HUGE : HOSTILE);
            m.put("chargeOn", "NOPE");
            m.put("percent", "-5");
            return m;
        }
        if (c == JsonNode.class || c == Object.class) {
            return mode == Mode.WRONG_TYPES ? "x" : Map.of("type", HOSTILE, "action", List.of(HOSTILE));
        }
        if (c.isRecord()) {
            if (mode == Mode.WRONG_TYPES && depth > 0) {
                return "notanobject";
            }
            Map<String, Object> m = new LinkedHashMap<>();
            for (RecordComponent rc : c.getRecordComponents()) {
                Object v = fuzz(rc.getGenericType(), mode, depth + 1);
                if (rc.getName().toLowerCase().contains("type") && mode == Mode.PLAUSIBLE && raw(rc.getGenericType()) == String.class) {
                    v = "text";
                }
                m.put(rc.getName(), v);
            }
            return m;
        }
        if (c == byte[].class) {
            return HOSTILE;
        }
        return mode == Mode.WRONG_TYPES ? List.of() : HOSTILE;
    }

    private static String pname(MethodParameter mp) {
        mp.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
        String n = mp.getParameterName();
        return n == null ? "" : n;
    }

    private static Class<?> raw(Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        return Object.class;
    }

    private Map<String, String> hostileQuery(Route r, boolean edge) {
        Map<String, String> q = new LinkedHashMap<>();
        for (MethodParameter mp : r.handler().getMethodParameters()) {
            RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
            if (rp == null || MultipartFile.class.isAssignableFrom(mp.getParameterType())) {
                continue;
            }
            String name = !rp.value().isEmpty() ? rp.value() : !rp.name().isEmpty() ? rp.name() : pname(mp);
            if (name == null || name.isEmpty()) {
                continue;
            }
            Class<?> t = mp.getParameterType();
            String v;
            if (edge) {
                v = switch (name) {
                    case "page" -> "-1";
                    case "size" -> "100000";
                    case "from" -> "2026-12-31";
                    case "to" -> "2026-01-01";
                    case "sort" -> "id;drop table users";
                    default -> sample(t, false);
                };
            } else {
                v = sample(t, true);
            }
            q.put(name, v);
        }
        return q.isEmpty() ? null : q;
    }

    private String sample(Class<?> t, boolean hostile) {
        if (t == Integer.class || t == int.class || t == Long.class || t == long.class) {
            return hostile ? "1e18" : "99999999999999999999";
        }
        if (t == LocalDate.class) {
            return hostile ? "2026-13-45" : "0000-00-00";
        }
        if (t == Boolean.class || t == boolean.class) {
            return "maybe";
        }
        if (t == BigDecimal.class) {
            return "12.123456789";
        }
        return hostile ? HOSTILE : "NOPE";
    }

    private MockMultipartFile badFile(String part, String kind) {
        return switch (kind) {
            case "zero" -> new MockMultipartFile(part, "empty.png", "image/png", new byte[0]);
            case "fake-png" -> new MockMultipartFile(part, "photo.png", "image/png", "this is not an image <?php system($_GET[c]); ?>".getBytes());
            case "svg-script" -> new MockMultipartFile(part, "logo.svg", "image/svg+xml",
                    "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());
            case "big" -> new MockMultipartFile(part, "big.png", "image/png", big());
            case "polyglot" -> new MockMultipartFile(part, "x.pdf.png", "application/pdf",
                    "%PDF-1.4\n<script>alert(1)</script>\n%%EOF GIF89a".getBytes());
            case "traversal-name" -> new MockMultipartFile(part, "../../../etc/passwd", "text/csv", "phone\n919812345678\n".getBytes());
            default -> null;
        };
    }

    private static byte[] cachedBig;

    private static byte[] big() {
        if (cachedBig == null) {
            cachedBig = new byte[3 * 1024 * 1024];
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
            System.arraycopy(png, 0, cachedBig, 0, png.length);
        }
        return cachedBig;
    }

    @SuppressWarnings("unused")
    private static Set<String> names(Parameter[] ps) {
        Set<String> s = new HashSet<>();
        for (Parameter p : ps) {
            PathVariable pv = p.getAnnotation(PathVariable.class);
            if (pv != null) {
                s.add(pv.value());
            }
        }
        return s;
    }
}
