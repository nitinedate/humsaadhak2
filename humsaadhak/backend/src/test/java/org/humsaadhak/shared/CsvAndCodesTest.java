package org.humsaadhak.shared;

import org.humsaadhak.shared.util.Codes;
import org.humsaadhak.shared.util.Csv;
import org.humsaadhak.shared.exception.DomainException;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvAndCodesTest {

    @Test
    void csvQuotesWhereNeededAndStartsWithAByteOrderMark() {
        String csv = Csv.of(List.of(List.of("Name", "Amount"), List.of("Asha Foundation, CSR", "450000"), List.of("Say \"hi\"", "")));
        assertThat(csv).isEqualTo("\uFEFFName,Amount\r\n\"Asha Foundation, CSR\",450000\r\n\"Say \"\"hi\"\"\",\r\n");
    }

    @Test
    void csvNeverLetsASpreadsheetRunAFormula() {
        assertThat(Csv.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(Csv.cell("+91 98200 00000")).isEqualTo("'+91 98200 00000");
        assertThat(Csv.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Csv.cell("-5000")).isEqualTo("-5000");          // a real negative number stays a number
        assertThat(Csv.cell(null)).isEqualTo("");
    }

    @Test
    void codesComeFromNames() {
        assertThat(Codes.fromName("Fire-safety certificate")).isEqualTo("FIRE_SAFETY_CERTIFICATE");
        assertThat(Codes.fromName("  Women & girls ")).isEqualTo("WOMEN_GIRLS");
        assertThat(Codes.fromName("Éducation spéciale")).isEqualTo("EDUCATION_SPECIALE");
        assertThat(Codes.fromName("x".repeat(60)).length()).isEqualTo(40);
        assertThatThrownBy(() -> Codes.fromName(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Codes.fromName("₹₹")).hasMessageContaining("letters or numbers");
    }
}
