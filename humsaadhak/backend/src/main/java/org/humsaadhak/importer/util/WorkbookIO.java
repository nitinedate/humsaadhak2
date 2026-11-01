package org.humsaadhak.importer.util;

import org.humsaadhak.importer.model.ImportType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.humsaadhak.shared.exception.DomainException;

/** Reads the first sheet of an .xlsx into rows keyed by header, and writes import templates. */
public final class WorkbookIO {
    public record Row(int number, Map<String, String> values) {}

    private WorkbookIO() {}

    public static List<Row> read(InputStream in, ImportType type) throws IOException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
            org.apache.poi.ss.usermodel.Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new DomainException("empty-file", "The sheet is empty.");
            Map<Integer, String> columns = new HashMap<>();
            header.forEach(c -> columns.put(c.getColumnIndex(), fmt.formatCellValue(c).trim()));
            List<String> missing = type.columns().stream().filter(c -> !columns.containsValue(c)).toList();
            if (!missing.isEmpty())
                throw new DomainException("missing-columns", "These columns are missing: " + String.join(", ", missing) + ". Download the template.");
            List<Row> rows = new ArrayList<>();
            for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> values = new HashMap<>();
                columns.forEach((idx, name) -> values.put(name, fmt.formatCellValue(row.getCell(idx)).trim()));
                if (values.values().stream().allMatch(String::isBlank)) continue;
                rows.add(new Row(r + 1, values));       // spreadsheet row numbers start at 1
                if (rows.size() > ImportType.MAX_ROWS)
                    throw new DomainException("too-many-rows", "Import at most " + ImportType.MAX_ROWS + " rows per file.");
            }
            return rows;
        }
    }

    public static byte[] template(ImportType type) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(type.name().toLowerCase());
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Row example = sheet.createRow(1);
            for (int i = 0; i < type.columns().size(); i++) {
                Cell h = head.createCell(i);
                h.setCellValue(type.columns().get(i));
                h.setCellStyle(bold);
                example.createCell(i).setCellValue(type.example().get(i));
                sheet.setColumnWidth(i, 28 * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
