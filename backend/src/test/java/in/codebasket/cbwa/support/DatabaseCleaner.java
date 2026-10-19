package in.codebasket.cbwa.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

/** Truncates every table except seed/reference tables between tests. */
public final class DatabaseCleaner {

    private static final Set<String> KEEP = Set.of("flyway_schema_history", "roles", "permissions", "role_permissions",
            "settings", "meta_settings", "credit_packs", "rate_plans", "number_sequences");

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = database() and table_type = 'BASE TABLE'",
                String.class);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String t : tables) {
                if (!KEEP.contains(t.toLowerCase())) {
                    jdbc.execute("TRUNCATE TABLE `" + t + "`");
                }
            }
            jdbc.update("delete from rate_plans where is_default = false");
            jdbc.update("delete from users");
            jdbc.update("delete from role_permissions where role_id in (select id from roles where code not in ('SUPER_ADMIN','API_PARTNER','SUPPORT','FINANCE'))");
            jdbc.update("delete from roles where code not in ('SUPER_ADMIN','API_PARTNER','SUPPORT','FINANCE')");
            jdbc.update("update number_sequences set next_value = 1");
            jdbc.update("update settings set value = 'DELIVERED' where group_code = 'CHARGING' and setting_key = 'charge_on'");
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
