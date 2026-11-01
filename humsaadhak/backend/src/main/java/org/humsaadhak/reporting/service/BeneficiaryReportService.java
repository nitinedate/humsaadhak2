package org.humsaadhak.reporting.service;

import org.humsaadhak.reporting.dto.ColumnOption;
import org.humsaadhak.reporting.dto.NeedOption;
import org.humsaadhak.reporting.dto.ReportOptions;
import org.humsaadhak.reporting.dto.ReportOrganisation;
import org.humsaadhak.reporting.dto.ReportRequest;
import org.humsaadhak.reporting.dto.ReportResult;

import org.humsaadhak.reporting.dao.BeneficiaryReportQuery;
import org.humsaadhak.reporting.util.BeneficiaryTerms;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.humsaadhak.reporting.dao.BeneficiaryReportQuery.CustomColumn;
import org.humsaadhak.reporting.dao.BeneficiaryReportQuery.Filter;
import org.humsaadhak.reporting.dao.BeneficiaryReportQuery.Type;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.reporting.dao.BeneficiaryReportDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the original project's beneficiary reports. Schools (direct beneficiaries) report on their causes and NGOs
 * (indirect beneficiaries) on their projects; staff run them for any beneficiary; donors cannot (as in the original).
 */
@Service
@Transactional(readOnly = true)
public class BeneficiaryReportService {
    public static final class NotAllowed extends RuntimeException { public NotAllowed(String m) { super(m); } }

    private final BeneficiaryReportDao reports;
    public BeneficiaryReportService(BeneficiaryReportDao reports) { this.reports = reports; }

    public ReportOptions options(CurrentUser me, Long requestedOrgId) {
        ReportOrganisation org = organisation(me, requestedOrgId);
        List<NeedOption> needs = reports.needOptions(org.id());
        LocalDate first = reports.firstActivity(org.id());
        List<ColumnOption> columns = Arrays.stream(CustomColumn.values()).map(c -> new ColumnOption(c.name(), c.label(org.school()))).toList();
        return new ReportOptions(org, needs, BeneficiaryReportQuery.years(first, LocalDate.now()), columns);
    }

    public ReportResult run(CurrentUser me, String typeName, ReportRequest r) {
        Type type = BeneficiaryReportQuery.type(typeName);
        ReportOrganisation org = organisation(me, r.orgId());
        if (r.needId() != null && !reports.needBelongsTo(r.needId(), org.id()))
            throw new NotFoundException("Cause or project", r.needId());
        List<CustomColumn> columns = type == Type.CUSTOM ? BeneficiaryReportQuery.customColumns(r.columns()) : null;
        Filter f = new Filter(org.id(), org.school(), r.needId(), blankToNull(r.year()), r.approvedFrom(), r.approvedTo(), columns);
        BeneficiaryReportQuery.Query q = BeneficiaryReportQuery.build(type, f);

        boolean all = Boolean.TRUE.equals(r.all());
        int size = all ? BeneficiaryReportQuery.MAX_EXPORT_ROWS : Math.max(1, Math.min(r.size() == null ? 10 : r.size(), 100));
        int page = all ? 0 : Math.max(0, r.page() == null ? 0 : r.page());
        long total = reports.count(q);
        List<Map<String, Object>> rows = reports.rows(q, size, (long) page * size).stream().map(BeneficiaryReportService::readable).toList();

        String needName = r.needId() == null ? null : reports.needName(r.needId());
        return new ReportResult(title(type, org, r.header()), BeneficiaryReportQuery.entityLine(org.name(), org.refNo()),
                BeneficiaryReportQuery.filterLine(f, needName), q.columns(), rows, total, page, size);
    }

    /** Stored codes become what people read; technical columns (prefixed with _) are dropped. */
    public static Map<String, Object> readable(Map<String, Object> raw) {
        Map<String, Object> v = new LinkedHashMap<>();
        raw.forEach((k, val) -> {
            if (k.startsWith("_")) return;
            v.put(k, val instanceof OffsetDateTime t ? t.toLocalDate().toString()
                    : val instanceof java.sql.Timestamp t ? t.toLocalDateTime().toLocalDate().toString()
                    : val instanceof java.sql.Date d ? d.toLocalDate().toString() : val);
        });
        if (raw.containsKey("_donorType") && raw.get("_donorType") != null) v.put("donorType", BeneficiaryTerms.singular((String) raw.get("_donorType")));
        else if (raw.containsKey("_donorType")) v.put("donorType", null);
        if (raw.containsKey("_periodicity")) v.put("periodicity", BeneficiaryTerms.periodicity((String) raw.get("_periodicity")));
        // the original showed donors as "Name [ID : BN100045 ]"
        if (raw.get("_donorRef") != null && v.get("donorName") != null) v.put("donorName", v.get("donorName") + " [ID: " + raw.get("_donorRef") + "]");
        return v;
    }

    private ReportOrganisation organisation(CurrentUser me, Long requested) {
        long orgId;
        if (me.role().isStaff()) {
            if (requested == null) throw new DomainException("org-required", "Choose the school or NGO to report on.");
            orgId = requested;
        } else if (me.role().isDonor()) {
            throw new NotAllowed("These reports are for schools and NGOs. Donors see their giving on My giving and the dashboard.");
        } else {
            if (me.orgId() == null) throw new NotAllowed("Your account is not linked to an organisation.");
            orgId = me.orgId();
        }
        ReportOrganisation org = reports.organisation(orgId).orElseThrow(() -> new NotFoundException("Organisation", orgId));
        if (!org.type().equals("SCHOOL") && !org.type().equals("NGO"))
            throw new DomainException("not-beneficiary", "Reports are run for a school or an NGO.");
        return org;
    }

    private static String title(Type type, ReportOrganisation org, String header) {
        if (header != null && !header.isBlank()) return header.trim();
        String need = org.school() ? "Cause" : "Project";
        return switch (type) {
            case DONATIONS -> "Donation Report";
            case MATCHES -> "Matches Report";
            case NEEDS -> need + " Report";
            case CUSTOM -> "Customize " + need + " Report";
        };
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
