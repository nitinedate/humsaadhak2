package org.humsaadhak.reporting.util;

import org.humsaadhak.reporting.dto.Bar;
import org.humsaadhak.reporting.dto.Drill;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.humsaadhak.reporting.util.BeneficiaryTerms.Flow;
import org.junit.jupiter.api.Test;

class BeneficiaryTermsTest {

    @Test
    void namesTheFourGroups() {
        assertThat(BeneficiaryTerms.group("DONOR_INDIVIDUAL")).isEqualTo("Individual donors");
        assertThat(BeneficiaryTerms.group("DONOR_CORPORATE")).isEqualTo("Corporate donors");
        assertThat(BeneficiaryTerms.group("SCHOOL")).isEqualTo("Direct beneficiaries (schools)");
        assertThat(BeneficiaryTerms.group("NGO")).isEqualTo("Indirect beneficiaries (NGOs)");
        assertThatThrownBy(() -> BeneficiaryTerms.group("GUEST")).isInstanceOf(IllegalArgumentException.class);
        assertThat(BeneficiaryTerms.receiver("CAUSE")).isEqualTo("Direct beneficiaries (schools)");
        assertThat(BeneficiaryTerms.receiver("PROJECT")).isEqualTo("Indirect beneficiaries (NGOs)");
        assertThat(BeneficiaryTerms.beneficiaryTitle("SCHOOL")).isEqualTo("Direct beneficiary dashboard");
        assertThat(BeneficiaryTerms.beneficiaryTitle("NGO")).isEqualTo("Indirect beneficiary dashboard");
    }

    @Test
    void reportRowsUseTheSingularName() {
        assertThat(BeneficiaryTerms.singular("DONOR_INDIVIDUAL")).isEqualTo("Individual donor");
        assertThat(BeneficiaryTerms.singular("DONOR_CORPORATE")).isEqualTo("Corporate donor");
        assertThat(BeneficiaryTerms.singular("SCHOOL")).isEqualTo("Direct beneficiary (school)");
        assertThat(BeneficiaryTerms.singular("NGO")).isEqualTo("Indirect beneficiary (NGO)");
    }

    @Test
    void whoGaveToWhomAlwaysHasAllFourCells() {
        List<Bar> bars = BeneficiaryTerms.whoGaveToWhom(List.of(
                new Flow("DONOR_INDIVIDUAL", "CAUSE", 110000), new Flow("DONOR_CORPORATE", "CAUSE", 500000),
                new Flow("DONOR_CORPORATE", "PROJECT", 300000), new Flow("DONOR_CORPORATE", "PROJECT", 40000)), "2026-04-01");
        assertThat(bars.get(0).label()).isEqualTo("Individual donors");
        assertThat(bars.get(0).values()).isEqualTo(List.of(110000.0, 0.0));
        assertThat(bars.get(1).values()).isEqualTo(List.of(500000.0, 340000.0));
        // clicking a bar lists exactly those donations
        assertThat(bars.get(1).drills().get(1)).isEqualTo(Drill.of("DONATIONS", "donorType", "DONOR_CORPORATE", "needKind", "PROJECT", "from", "2026-04-01"));
        var empty = BeneficiaryTerms.whoGaveToWhom(List.of(), "2026-04-01");
        assertThat(empty.get(0).values()).isEqualTo(List.of(0.0, 0.0));
        assertThat(empty.get(1).values()).isEqualTo(List.of(0.0, 0.0));
    }

    @Test
    void financialYearQuartersStartInApril() {
        assertThat(BeneficiaryTerms.fyQuarter(4)).isEqualTo("Q1");
        assertThat(BeneficiaryTerms.fyQuarter(6)).isEqualTo("Q1");
        assertThat(BeneficiaryTerms.fyQuarter(7)).isEqualTo("Q2");
        assertThat(BeneficiaryTerms.fyQuarter(12)).isEqualTo("Q3");
        assertThat(BeneficiaryTerms.fyQuarter(1)).isEqualTo("Q4");
        assertThat(BeneficiaryTerms.fyQuarter(3)).isEqualTo("Q4");
    }

    @Test
    void notesReadNaturally() {
        assertThat(BeneficiaryTerms.supportedNote(4, 2)).isEqualTo("4 direct and 2 indirect beneficiaries");
        assertThat(BeneficiaryTerms.supportedNote(1, 0)).isEqualTo("1 direct beneficiary");
        assertThat(BeneficiaryTerms.supportedNote(0, 3)).isEqualTo("3 indirect beneficiaries");
        assertThat(BeneficiaryTerms.supportedNote(0, 0)).isEqualTo("none yet");
        assertThat(BeneficiaryTerms.donorMixNote(2, 3)).isEqualTo("2 corporate, 3 individual");
        assertThat(BeneficiaryTerms.donorMixNote(0, 1)).isEqualTo("1 individual");
        assertThat(BeneficiaryTerms.donorMixNote(0, 0)).isEqualTo("no donations yet");
    }
}
