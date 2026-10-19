package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the first sheet of an uploaded CSV or XLSX file into a header row + data rows.
 * XLSX is read with the streaming SAX API so large files do not need the whole workbook in memory.
 * Empty rows are skipped. Throws IMPORT_INVALID_FILE for unreadable files or when maxRows is exceeded.
 */
public final class TabularFile {

    private final List<String> columns;
    private final List<List<String>> rows;

    private TabularFile(List<String> columns, List<List<String>> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public List<String> columns() {
        return columns;
    }

    public List<List<String>> rows() {
        return rows;
    }

    /** Row values keyed by column name (missing cells -> ""). */
    public Map<String, String> rowMap(int index) {
        List<String> r = rows.get(index);
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            m.put(columns.get(i), i < r.size() && r.get(i) != null ? r.get(i).trim() : "");
        }
        return m;
    }

    public static boolean isXlsx(String mimeType) {
        return mimeType != null && (mimeType.contains("spreadsheetml") || mimeType.contains("ooxml"));
    }

    public static TabularFile read(byte[] bytes, String mimeType, int maxRows) {
        List<List<String>> all = isXlsx(mimeType) ? readXlsx(bytes, maxRows + 1) : readCsv(bytes, maxRows + 1);
        if (all.isEmpty()) {
            throw new AppException(ErrorCode.IMPORT_INVALID_FILE, "The file is empty");
        }
        List<String> header = new ArrayList<>();
        int n = 1;
        for (String h : all.get(0)) {
            String name = h == null ? "" : h.trim();
            if (name.isEmpty()) {
                name = "Column " + n;
            }
            String unique = name;
            int k = 2;
            while (header.contains(unique)) {
                unique = name + "_" + k++;
            }
            header.add(unique);
            n++;
        }
        List<List<String>> data = new ArrayList<>(all.subList(1, all.size()));
        if (data.size() > maxRows) {
            throw new AppException(ErrorCode.IMPORT_INVALID_FILE, "The file has more than " + maxRows + " rows");
        }
        return new TabularFile(header, data);
    }

    private static List<List<String>> readCsv(byte[] bytes, int limit) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        int nl = text.indexOf('\n');
        String first = nl >= 0 ? text.substring(0, nl) : text;
        char delimiter = ',';
        if (first.indexOf(',') < 0) {
            if (first.indexOf(';') >= 0) {
                delimiter = ';';
            } else if (first.indexOf('\t') >= 0) {
                delimiter = '\t';
            }
        }
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).setTrim(true).build();
        List<List<String>> out = new ArrayList<>();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (CSVRecord rec : parser) {
                List<String> row = new ArrayList<>(rec.size());
                rec.forEach(row::add);
                if (row.stream().allMatch(v -> v == null || v.isBlank())) {
                    continue;
                }
                out.add(row);
                if (out.size() > limit) {
                    throw new AppException(ErrorCode.IMPORT_INVALID_FILE, "The file has more than " + (limit - 1) + " rows");
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.IMPORT_INVALID_FILE, "The CSV file could not be read");
        }
        return out;
    }

    /** Formats whole numbers in "General" cells as plain digits (phone numbers must not become 9.19E+11). */
    private static final class PlainNumberFormatter extends DataFormatter {
        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString) {
            if ((formatString == null || "General".equalsIgnoreCase(formatString) || formatIndex == 0)
                    && value == Math.rint(value) && !Double.isInfinite(value)) {
                return new BigDecimal(value).toPlainString();
            }
            return super.formatRawCellContents(value, formatIndex, formatString);
        }
    }

    private static List<List<String>> readXlsx(byte[] bytes, int limit) {
        List<List<String>> out = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                return out;
            }
            try (InputStream sheet = sheets.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, new XSSFSheetXMLHandler.SheetContentsHandler() {
                    private List<String> current = new ArrayList<>();

                    @Override
                    public void startRow(int rowNum) {
                        current = new ArrayList<>();
                    }

                    @Override
                    public void endRow(int rowNum) {
                        if (current.stream().allMatch(v -> v == null || v.isBlank())) {
                            return;
                        }
                        out.add(current);
                        if (out.size() > limit) {
                            throw new AppException(ErrorCode.IMPORT_INVALID_FILE, "The file has more than " + (limit - 1) + " rows");
                        }
                    }

                    @Override
                    public void cell(String ref, String formattedValue, XSSFComment comment) {
                        int col = ref == null ? current.size() : new CellReference(ref).getCol();
                        while (current.size() < col) {
                            current.add("");
                        }
                        current.add(formattedValue == null ? "" : formattedValue);
                    }
                }, new PlainNumberFormatter(), false));
                parser.parse(new InputSource(sheet));
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.IMPORT_INVALID_FILE, "The Excel file could not be read");
        }
        return out;
    }

    /** Builds a UTF-8 CSV document with BOM (opens correctly in Excel). */
    public static byte[] toCsv(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        try (CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT)) {
            printer.printRecord(header);
            for (List<String> r : rows) {
                printer.printRecord(r);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
