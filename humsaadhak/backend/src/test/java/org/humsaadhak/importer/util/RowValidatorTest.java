package org.humsaadhak.importer.util;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.humsaadhak.catalogue.api.NeedKind;
import org.humsaadhak.shared.model.Periodicity;
import org.junit.jupiter.api.Test;

class RowValidatorTest {
    private static final Set<String> SECTORS = Set.of("Education", "Nutrition", "Health and sanitation");
    private static final String DESC = "Shared tablets with offline lessons in Marathi for six rural schools.";

    private static Map<String, String> needRow(String kind, String sector, String amount, String periodicity) {
        Map<String, String> m = new HashMap<>();
        m.put("Organisation ref no", "IB100412"); m.put("Kind", kind); m.put("Name", "Tablet learning");
        m.put("Description", DESC); m.put("Sector", sector); m.put("Target amount", amount); m.put("Periodicity", periodicity);
        return m;
    }

    @Test
    void parsesAmountsAsPeopleTypeThem() {
        assertThat(RowValidator.parseAmount("4,50,000")).isEqualByComparingTo(new BigDecimal("450000"));
        assertThat(RowValidator.parseAmount("₹4,50,000.50")).isEqualByComparingTo(new BigDecimal("450000.50"));
        assertThat(RowValidator.parseAmount("Rs. 1200")).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(RowValidator.parseAmount("four lakh")).isEqualTo(null);
        assertThat(RowValidator.parseAmount("  ")).isEqualTo(null);
    }

    @Test
    void parsesPeriodicityNames() {
        assertThat(RowValidator.parsePeriodicity("One-time")).isEqualTo(Periodicity.ONE_TIME);
        assertThat(RowValidator.parsePeriodicity("half yearly")).isEqualTo(Periodicity.HALF_YEARLY);
        assertThat(RowValidator.parsePeriodicity("HALF_YEARLY")).isEqualTo(Periodicity.HALF_YEARLY);
        assertThat(RowValidator.parsePeriodicity("Annual")).isEqualTo(Periodicity.YEARLY);
        assertThat(RowValidator.parsePeriodicity("weekly")).isEqualTo(null);
    }

    @Test
    void validNeedRowIsParsedWithCanonicalSector() {
        List<RowValidator.RowError> errors = new ArrayList<>();
        var parsed = RowValidator.need(2, needRow("project", "education", "3,80,000", "One-time"), SECTORS, errors);
        assertThat(errors.isEmpty()).isTrue();
        assertThat(parsed.isPresent()).isTrue();
        assertThat(parsed.get().kind()).isEqualTo(NeedKind.PROJECT);
        assertThat(parsed.get().sector()).isEqualTo("Education");
        assertThat(parsed.get().target()).isEqualByComparingTo(new BigDecimal("380000"));
    }

    @Test
    void everyProblemIsReportedWithRowAndColumn() {
        List<RowValidator.RowError> errors = new ArrayList<>();
        Map<String, String> row = needRow("grant", "Sports", "0", "weekly");
        row.put("Description", "too short");
        var parsed = RowValidator.need(7, row, SECTORS, errors);
        assertThat(parsed.isPresent()).isFalse();
        assertThat(errors.size()).isEqualTo(5);
        assertThat(errors.stream().allMatch(e -> e.row() == 7)).isTrue();
        assertThat(errors.stream().map(RowValidator.RowError::column).toList())
                .isEqualTo(List.of("Kind", "Description", "Sector", "Target amount", "Periodicity"));
    }

    @Test
    void purposeAmountIsOptionalButMustBePositiveWhenGiven() {
        Map<String, String> row = new HashMap<>(Map.of("Donor ref no", "BN100011", "Name", "Digital classrooms",
                "Description", DESC, "Sector", "Education", "Periodicity", "Yearly"));
        List<RowValidator.RowError> errors = new ArrayList<>();
        assertThat(RowValidator.purpose(2, row, SECTORS, errors).isPresent()).isTrue();
        row.put("Commitment amount", "-5");
        assertThat(RowValidator.purpose(3, row, SECTORS, errors).isPresent()).isFalse();
        assertThat(errors.get(0).column()).isEqualTo("Commitment amount");
    }

    @Test
    void missingRequiredFieldsAreNamed() {
        List<RowValidator.RowError> errors = new ArrayList<>();
        Map<String, String> row = needRow("Cause", "Education", "100", "Monthly");
        row.remove("Organisation ref no");
        row.put("Name", " ");
        RowValidator.need(4, row, SECTORS, errors);
        assertThat(errors.stream().map(RowValidator.RowError::column).toList()).isEqualTo(List.of("Organisation ref no", "Name"));
    }
}
