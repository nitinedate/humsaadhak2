package org.humsaadhak.reporting.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.humsaadhak.reporting.dao.CsvExportDao;
import org.humsaadhak.reporting.dto.CsvFile;
import org.humsaadhak.reporting.util.BeneficiaryTerms;
import org.humsaadhak.reporting.util.ReportFormat;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.util.Csv;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the staff CSV downloads (they open directly in Excel) with readable values. */
@Service
@Transactional(readOnly = true)
public class CsvExportService {
    private final CsvExportDao exports;

    public CsvExportService(CsvExportDao exports) { this.exports = exports; }

    public CsvFile donations(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate start = from != null ? from : ReportFormat.fyStart(today);
        LocalDate end = to != null ? to : today;
        if (end.isBefore(start)) throw new DomainException("bad-range", "The end date is before the start date.");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Reference", "Paid on", "Donor", "Donor type", "Cause or project", "Beneficiary", "Beneficiary type",
                "Paid (INR)", "Pledged (INR)", "Payment reference", "Matched"));
        exports.donations(start, end).forEach(d -> rows.add(List.of(str(d.refNo()), d.paidOn().toString(), d.donor(), BeneficiaryTerms.singular(d.donorType()),
                d.need(), d.beneficiary(), BeneficiaryTerms.singular(d.beneficiaryType()), money(d.paid()), money(d.pledged()), str(d.paymentRef()),
                d.matched() ? "Yes" : "No")));
        return new CsvFile("donations-" + start + "-to-" + end + ".csv", Csv.of(rows));
    }

    public CsvFile organisations(LocalDate today) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Reference", "Name", "Type", "City", "State", "Verified", "Registered on"));
        exports.organisations().forEach(o -> rows.add(List.of(str(o.refNo()), o.name(), BeneficiaryTerms.singular(o.type()), str(o.city()), str(o.state()),
                o.verified() ? "Yes" : "No", o.registeredOn() == null ? "" : o.registeredOn().toString())));
        return new CsvFile("organisations-" + today + ".csv", Csv.of(rows));
    }

    public CsvFile needs(LocalDate today) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Reference", "Name", "Kind", "Beneficiary", "Beneficiary type", "Sector", "Target (INR)", "Raised (INR)",
                "Still needed (INR)", "Approved", "Completed"));
        exports.needs().forEach(n -> rows.add(List.of(str(n.refNo()), n.name(), "CAUSE".equals(n.kind()) ? "Cause" : "Project", n.beneficiary(),
                BeneficiaryTerms.singular(n.beneficiaryType()), n.sector(), money(n.target()), money(n.raised()), money(n.remaining()),
                n.approved() ? "Yes" : "No", n.completed() ? "Yes" : "No")));
        return new CsvFile("causes-and-projects-" + today + ".csv", Csv.of(rows));
    }

    private static String money(BigDecimal v) { return v == null ? "" : v.stripTrailingZeros().toPlainString(); }
    private static String str(String v) { return v == null ? "" : v; }
}
