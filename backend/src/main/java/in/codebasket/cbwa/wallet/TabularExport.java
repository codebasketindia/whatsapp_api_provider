package in.codebasket.cbwa.wallet;

import in.codebasket.cbwa.common.exception.AppException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes simple tables as CSV (UTF-8 with BOM for Excel) or XLSX (streaming). */
public final class TabularExport {

    private TabularExport() {
    }

    public static ResponseEntity<byte[]> response(String format, String baseName, List<String> headers, List<List<Object>> rows) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        if (!xlsx && format != null && !"csv".equalsIgnoreCase(format)) {
            throw AppException.field("format", "Use csv or xlsx");
        }
        byte[] body = xlsx ? xlsx(headers, rows) : csv(headers, rows);
        String filename = baseName + (xlsx ? ".xlsx" : ".csv");
        return ResponseEntity.ok()
                .contentType(xlsx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    public static byte[] csv(List<String> headers, List<List<Object>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build())) {
                for (List<Object> r : rows) {
                    printer.printRecord(r.stream().map(TabularExport::text).toList());
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] xlsx(List<String> headers, List<List<Object>> rows) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Data");
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(bold);
            }
            int r = 1;
            for (List<Object> row : rows) {
                Row x = sheet.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    Object v = row.get(i);
                    Cell c = x.createCell(i);
                    if (v instanceof BigDecimal bd) {
                        c.setCellValue(bd.doubleValue());
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(text(v));
                    }
                }
            }
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        String s = String.valueOf(v);
        // Neutralise spreadsheet formula injection
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) {
            return "'" + s;
        }
        return s;
    }
}
