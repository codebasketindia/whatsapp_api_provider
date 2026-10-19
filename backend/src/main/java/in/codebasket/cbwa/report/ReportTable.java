package in.codebasket.cbwa.report;

import java.util.List;

/**
 * Format-neutral table used by the exporters. Column types drive formatting:
 * TEXT, INT, MONEY (numeric, 2 decimals in exports), PERCENT.
 */
public record ReportTable(String title, String subtitle, List<Column> columns, List<List<Object>> rows) {

    public enum Type { TEXT, INT, MONEY, PERCENT }

    public record Column(String header, Type type) {
    }

    public static Column text(String h) {
        return new Column(h, Type.TEXT);
    }

    public static Column num(String h) {
        return new Column(h, Type.INT);
    }

    public static Column money(String h) {
        return new Column(h, Type.MONEY);
    }
}
