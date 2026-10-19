package in.codebasket.cbwa.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders a ReportTable as XLSX (SXSSF), PDF (OpenPDF) or CSV. */
@Component
@RequiredArgsConstructor
public class ReportExporter {

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    public static final MediaType PDF = MediaType.APPLICATION_PDF;
    public static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final SettingsService settings;

    public ResponseEntity<byte[]> export(ReportTable table, String format, String baseName) {
        String f = format == null ? "xlsx" : format.toLowerCase(Locale.ROOT);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(TimeUtil.nowIst());
        return switch (f) {
            case "xlsx" -> file(xlsx(table), XLSX, baseName + "-" + stamp + ".xlsx");
            case "pdf" -> file(pdf(table), PDF, baseName + "-" + stamp + ".pdf");
            case "csv" -> file(csv(table), CSV, baseName + "-" + stamp + ".csv");
            default -> throw AppException.field("format", "format must be json, xlsx, pdf or csv");
        };
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, MediaType type, String name) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(bytes);
    }

    public byte[] xlsx(ReportTable t) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = wb.createSheet(safeSheetName(t.title()));
            CellStyle header = wb.createCellStyle();
            var bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle titleStyle = wb.createCellStyle();
            var titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            int r = 0;
            Row title = sheet.createRow(r++);
            Cell tc = title.createCell(0);
            tc.setCellValue(companyName() + " - " + t.title());
            tc.setCellStyle(titleStyle);
            if (t.subtitle() != null) {
                sheet.createRow(r++).createCell(0).setCellValue(t.subtitle());
            }
            r++;
            Row h = sheet.createRow(r++);
            for (int c = 0; c < t.columns().size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(t.columns().get(c).header());
                cell.setCellStyle(header);
                sheet.setColumnWidth(c, Math.max(12, t.columns().get(c).header().length() + 4) * 256);
            }
            for (List<Object> rowData : t.rows()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < t.columns().size(); c++) {
                    Object v = c < rowData.size() ? rowData.get(c) : null;
                    Cell cell = row.createCell(c);
                    switch (t.columns().get(c).type()) {
                        case MONEY -> {
                            cell.setCellValue(toBig(v).setScale(2, RoundingMode.HALF_UP).doubleValue());
                            cell.setCellStyle(money);
                        }
                        case INT, PERCENT -> cell.setCellValue(toBig(v).doubleValue());
                        default -> cell.setCellValue(v == null ? "" : String.valueOf(v));
                    }
                }
            }
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build Excel file", e);
        }
    }

    public byte[] pdf(ReportTable t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(t.columns().size() > 6 ? PageSize.A4.rotate() : PageSize.A4, 24, 24, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        doc.add(new Paragraph(companyName(), titleFont));
        doc.add(new Paragraph(t.title() + (t.subtitle() == null ? "" : " - " + t.subtitle()), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        doc.add(new Paragraph("Generated " + DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").format(TimeUtil.nowIst()) + " IST", small));
        doc.add(new Paragraph(" "));
        PdfPTable table = new PdfPTable(t.columns().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (ReportTable.Column c : t.columns()) {
            PdfPCell cell = new PdfPCell(new Phrase(c.header(), headFont));
            cell.setBackgroundColor(new Color(0x12, 0x8C, 0x7E));
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (List<Object> row : t.rows()) {
            for (int c = 0; c < t.columns().size(); c++) {
                Object v = c < row.size() ? row.get(c) : null;
                ReportTable.Type type = t.columns().get(c).type();
                String text = switch (type) {
                    case MONEY -> String.format(Locale.ENGLISH, "%,.2f", toBig(v).setScale(2, RoundingMode.HALF_UP));
                    case INT -> String.valueOf(toBig(v).longValue());
                    case PERCENT -> toBig(v).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
                    default -> v == null ? "" : String.valueOf(v);
                };
                PdfPCell cell = new PdfPCell(new Phrase(text, small));
                cell.setPadding(3);
                if (type != ReportTable.Type.TEXT) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                table.addCell(cell);
            }
        }
        if (t.rows().isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("No data for the selected period", small));
            cell.setColspan(t.columns().size());
            cell.setPadding(6);
            table.addCell(cell);
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    public byte[] csv(ReportTable t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter p = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM so Excel opens ₹ correctly
            p.printRecord(t.columns().stream().map(ReportTable.Column::header).toList());
            for (List<Object> row : t.rows()) {
                java.util.List<Object> vals = new java.util.ArrayList<>();
                for (int c = 0; c < t.columns().size(); c++) {
                    Object v = c < row.size() ? row.get(c) : null;
                    vals.add(t.columns().get(c).type() == ReportTable.Type.MONEY ? toBig(v).setScale(2, RoundingMode.HALF_UP).toPlainString()
                            : v == null ? "" : v);
                }
                p.printRecord(vals);
            }
            p.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build CSV file", e);
        }
        return out.toByteArray();
    }

    private String companyName() {
        return settings.getString(SettingKeys.COMPANY, "name", "CB WhatsApp Gateway");
    }

    private static BigDecimal toBig(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String safeSheetName(String s) {
        String n = s.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return n.length() > 31 ? n.substring(0, 31) : n;
    }
}
