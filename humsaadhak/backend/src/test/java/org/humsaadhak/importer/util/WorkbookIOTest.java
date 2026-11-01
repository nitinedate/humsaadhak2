package org.humsaadhak.importer.util;

import org.humsaadhak.importer.model.ImportType;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.humsaadhak.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class WorkbookIOTest {

    @Test
    void templateReadsBackAsOneExampleRow() throws Exception {
        for (ImportType type : ImportType.values()) {
            var rows = WorkbookIO.read(new ByteArrayInputStream(WorkbookIO.template(type)), type);
            assertThat(rows.size()).isEqualTo(1);
            assertThat(rows.get(0).number()).isEqualTo(2);
            assertThat(rows.get(0).values().get("Name")).isEqualTo(type.example().get(type.columns().indexOf("Name")));
        }
    }

    @Test
    void missingColumnsAreNamed() throws Exception {
        byte[] bytes;
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            wb.createSheet().createRow(0).createCell(0).setCellValue("Name");
            wb.write(out);
            bytes = out.toByteArray();
        }
        assertThatThrownBy(() -> WorkbookIO.read(new ByteArrayInputStream(bytes), ImportType.NEEDS))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Organisation ref no");
    }
}
