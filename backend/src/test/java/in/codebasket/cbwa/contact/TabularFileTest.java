package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.common.exception.AppException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabularFileTest {

    @Test
    void readsXlsxWithNumericPhonesAsPlainDigits() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("List");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("phone");
            h.createCell(1).setCellValue("name");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue(919876543210d);
            r.createCell(1).setCellValue("Ravi");
            s.createRow(2); // empty row is skipped
            Row r3 = s.createRow(3);
            r3.createCell(1).setCellValue("No phone");
            wb.write(out);
            bytes = out.toByteArray();
        }
        TabularFile t = TabularFile.read(bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 10);
        assertThat(t.columns()).containsExactly("phone", "name");
        assertThat(t.rows()).hasSize(2);
        assertThat(t.rowMap(0)).containsEntry("phone", "919876543210").containsEntry("name", "Ravi");
        assertThat(t.rowMap(1)).containsEntry("phone", "").containsEntry("name", "No phone");
    }

    @Test
    void readsSemicolonCsvWithBomAndEnforcesRowLimit() {
        byte[] csv = "﻿phone;name\n9876543210;A\n9876543211;B\n".getBytes(StandardCharsets.UTF_8);
        TabularFile t = TabularFile.read(csv, "text/csv", 10);
        assertThat(t.columns()).containsExactly("phone", "name");
        assertThat(t.rowMap(1)).containsEntry("name", "B");
        assertThatThrownBy(() -> TabularFile.read(csv, "text/csv", 1)).isInstanceOf(AppException.class);
    }
}
