package in.codebasket.cbwa.support;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives every test a pristine database: business tables are truncated and the seed/reference tables
 * (roles, permissions, settings, Meta settings, credit packs, rate plans, sequences) are restored to
 * the exact Flyway seed state. The first call in a JVM runs Flyway clean + migrate so data left by an
 * earlier run (or by destructive tests such as the API torture suite) can never leak into a test.
 */
public final class DatabaseCleaner {

    private static final List<String> SEED_TABLES = List.of("permissions", "roles", "role_permissions", "settings",
            "meta_settings", "credit_packs", "rate_plans", "number_sequences");

    private static Map<String, List<Map<String, Object>>> seed;

    private DatabaseCleaner() {
    }

    public static synchronized void clean(JdbcTemplate jdbc, Flyway flyway) {
        if (seed == null) {
            flyway.clean();
            flyway.migrate();
            seed = new LinkedHashMap<>();
            for (String t : SEED_TABLES) {
                seed.put(t, jdbc.queryForList("select * from `" + t + "`"));
            }
        }
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = database() and table_type = 'BASE TABLE'",
                String.class);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String t : tables) {
                if (!t.equalsIgnoreCase("flyway_schema_history")) {
                    jdbc.execute("TRUNCATE TABLE `" + t + "`");
                }
            }
            for (Map.Entry<String, List<Map<String, Object>>> e : seed.entrySet()) {
                for (Map<String, Object> row : e.getValue()) {
                    List<String> cols = new ArrayList<>(row.keySet());
                    String sql = "insert into `" + e.getKey() + "` (" + String.join(",", cols.stream().map(c -> "`" + c + "`").toList())
                            + ") values (" + String.join(",", cols.stream().map(c -> "?").toList()) + ")";
                    jdbc.update(sql, cols.stream().map(row::get).toArray());
                }
            }
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
