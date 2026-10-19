package in.codebasket.cbwa.common.settings;

import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key-value settings managed by admins. Encrypted values are stored with AES-GCM and are never
 * returned to clients - the API only exposes "<key>Set": true/false for them.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingRepository repository;
    private final CryptoService crypto;
    private final AuditService auditService;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final String NULL_MARKER = "\u0000";

    public String getString(String group, String key) {
        String cacheKey = group + "." + key;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return NULL_MARKER.equals(cached) ? null : cached;
        }
        String value = repository.findByGroupCodeAndSettingKey(group, key)
                .map(s -> s.isEncrypted() ? crypto.decrypt(s.getValue()) : s.getValue())
                .orElse(null);
        cache.put(cacheKey, value == null ? NULL_MARKER : value);
        return value;
    }

    public String getString(String group, String key, String def) {
        String v = getString(group, key);
        return v == null || v.isBlank() ? def : v;
    }

    public boolean getBool(String group, String key, boolean def) {
        String v = getString(group, key);
        return v == null || v.isBlank() ? def : Boolean.parseBoolean(v.trim());
    }

    public int getInt(String group, String key, int def) {
        String v = getString(group, key);
        try {
            return v == null || v.isBlank() ? def : new BigDecimal(v.trim()).intValue();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public BigDecimal getDecimal(String group, String key, BigDecimal def) {
        String v = getString(group, key);
        try {
            return v == null || v.isBlank() ? def : new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Group values for admin UI (camelCase keys); secrets are replaced with "<key>Set" flags. */
    @Transactional(readOnly = true)
    public Map<String, Object> getGroupForApi(String group) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Setting s : repository.findByGroupCodeOrderById(group)) {
            String name = camel(s.getSettingKey());
            if (s.isEncrypted()) {
                out.put(name + "Set", s.getValue() != null && !s.getValue().isBlank());
            } else {
                out.put(name, typed(s));
            }
        }
        return out;
    }

    /**
     * Updates a group from camelCase keys. Unknown keys are rejected; encrypted keys are only
     * changed when a non-blank value is sent (blank = keep existing secret).
     */
    @Transactional
    public Map<String, Object> updateGroup(String group, Map<String, Object> values) {
        List<Setting> settings = repository.findByGroupCodeOrderById(group);
        if (settings.isEmpty()) {
            throw AppException.notFound("Settings group");
        }
        Map<String, Setting> byCamel = new LinkedHashMap<>();
        settings.forEach(s -> byCamel.put(camel(s.getSettingKey()), s));
        Map<String, Object> oldValues = getGroupForApi(group);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Setting s = byCamel.get(e.getKey());
            if (s == null) {
                if (e.getKey().endsWith("Set")) {
                    continue;
                }
                throw AppException.field(e.getKey(), "Unknown setting");
            }
            Object raw = e.getValue();
            String str = raw == null ? null : String.valueOf(raw).trim();
            if (s.isEncrypted()) {
                if (str != null && !str.isEmpty()) {
                    s.setValue(crypto.encrypt(str));
                }
                continue;
            }
            validate(s, e.getKey(), str);
            s.setValue(str == null || str.isEmpty() ? null : str);
        }
        repository.saveAll(settings);
        cache.keySet().removeIf(k -> k.startsWith(group + "."));
        Map<String, Object> newValues = getGroupForApi(group);
        auditService.log("SETTINGS_UPDATE", "SETTINGS", group, oldValues, newValues);
        return newValues;
    }

    /** Internal update used by services (e.g. logo upload) - same validation and cache handling. */
    @Transactional
    public void set(String group, String key, String value) {
        Setting s = repository.findByGroupCodeAndSettingKey(group, key)
                .orElseThrow(() -> AppException.notFound("Setting " + group + "." + key));
        s.setValue(s.isEncrypted() && value != null ? crypto.encrypt(value) : value);
        repository.save(s);
        cache.remove(group + "." + key);
    }

    public void evictAll() {
        cache.clear();
    }

    private void validate(Setting s, String field, String v) {
        if (v == null || v.isEmpty()) {
            return;
        }
        switch (s.getValueType()) {
            case "NUMBER" -> {
                try {
                    if (new BigDecimal(v).signum() < 0) {
                        throw AppException.field(field, "Must be zero or more");
                    }
                } catch (NumberFormatException ex) {
                    throw AppException.field(field, "Must be a number");
                }
            }
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                    throw AppException.field(field, "Must be true or false");
                }
            }
            default -> {
                if (v.length() > 2000) {
                    throw AppException.field(field, "Too long");
                }
            }
        }
        if ("charge_on".equals(s.getSettingKey()) && !v.equals("SENT") && !v.equals("DELIVERED")) {
            throw AppException.field(field, "Must be SENT or DELIVERED");
        }
        if ("active_gateway".equals(s.getSettingKey()) && !v.equals("NONE") && !v.equals("RAZORPAY")) {
            throw AppException.field(field, "Unsupported gateway");
        }
        if ("percent".equals(s.getSettingKey()) && new BigDecimal(v).compareTo(new BigDecimal("100")) > 0) {
            throw AppException.field(field, "Must be 100 or less");
        }
    }

    private static Object typed(Setting s) {
        String v = s.getValue();
        if (v == null) {
            return null;
        }
        return switch (s.getValueType()) {
            case "NUMBER" -> {
                BigDecimal bd = new BigDecimal(v).stripTrailingZeros();
                yield bd.scale() <= 0 ? (Object) bd.longValue() : (Object) bd.doubleValue();
            }
            case "BOOLEAN" -> Boolean.parseBoolean(v);
            default -> v;
        };
    }

    public static String camel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }
}
