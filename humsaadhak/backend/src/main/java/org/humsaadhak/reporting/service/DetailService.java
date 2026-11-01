package org.humsaadhak.reporting.service;

import org.springframework.security.access.AccessDeniedException;
import org.humsaadhak.reporting.dto.DetailChild;
import org.humsaadhak.reporting.dto.DetailColumn;
import org.humsaadhak.reporting.dto.DetailRow;
import org.humsaadhak.reporting.dto.DetailTable;

import org.humsaadhak.reporting.util.BeneficiaryTerms;
import org.humsaadhak.reporting.dao.DetailQuery;

import java.util.*;
import java.util.stream.Collectors;
import org.humsaadhak.reporting.dao.DetailQuery.Scope;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.reporting.dao.DetailDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a drill-down (DetailQuery) and attaches the rows each record expands into, as the original dashboards did:
 * a beneficiary opens into its causes or projects, a donor into its donations, a cause or project into the donations
 * it received, a purpose into its confirmed matches.
 */
@Service
@Transactional(readOnly = true)
public class DetailService {
    private static final List<DetailColumn> CHILD_NEEDS = List.of(new DetailColumn("ref", "Reference", "text"), new DetailColumn("name", "Cause or project", "text"),
            new DetailColumn("target", "Target", "money"), new DetailColumn("raised", "Raised", "money"));
    private static final List<DetailColumn> CHILD_DONATIONS = List.of(new DetailColumn("ref", "Reference", "text"), new DetailColumn("paidOn", "Paid on", "date"),
            new DetailColumn("party", "Donor or beneficiary", "text"), new DetailColumn("need", "Cause or project", "text"), new DetailColumn("amount", "Paid", "money"));
    private static final List<DetailColumn> CHILD_MATCHES = List.of(new DetailColumn("priority", "Priority", "count"), new DetailColumn("ref", "Match", "text"),
            new DetailColumn("need", "Cause or project", "text"), new DetailColumn("beneficiary", "Beneficiary", "text"), new DetailColumn("remaining", "Still needed", "money"));

    private final DetailDao details;
    public DetailService(DetailDao details) { this.details = details; }

    public DetailTable load(String setName, Map<String, String> filters, CurrentUser me) {
        DetailQuery.Set set = DetailQuery.set(setName);
        DetailQuery.Sql q;
        try {
            q = DetailQuery.build(set, filters, scopeOf(me));
        } catch (DetailQuery.NotAllowed e) {
            throw new AccessDeniedException(e.getMessage());
        }
        long total = details.count(q);
        List<Map<String, Object>> raw = details.rows(q);
        List<Long> ids = raw.stream().map(r -> ((Number) r.get("_id")).longValue()).toList();
        Map<Long, DetailChild> children = children(set, raw, ids, me);
        List<DetailRow> rows = raw.stream().map(r -> new DetailRow(visible(set, r), children.get(((Number) r.get("_id")).longValue()))).toList();
        return new DetailTable(total, total > rows.size(), q.columns(), rows);
    }

    public static Scope scopeOf(CurrentUser me) {
        if (me.role().isStaff()) return Scope.staffScope();
        if (me.orgId() == null) throw new AccessDeniedException("not linked to an organisation");
        return me.role().isDonor() ? Scope.donor(me.orgId()) : Scope.beneficiary(me.orgId());
    }

    /** Hides technical columns (prefixed with _) and turns stored codes into the names people use. */
    private static Map<String, Object> visible(DetailQuery.Set set, Map<String, Object> r) {
        Map<String, Object> v = new LinkedHashMap<>();
        r.forEach((k, val) -> { if (!k.startsWith("_")) v.put(k, val); });
        switch (set) {
            case ORGANISATIONS -> v.put("group", BeneficiaryTerms.singular((String) r.get("_type")));
            case DONATIONS -> {
                v.put("donorGroup", BeneficiaryTerms.singular((String) r.get("_donorType")));
                v.put("beneficiaryGroup", BeneficiaryTerms.singular((String) r.get("_ownerType")));
            }
            case PURPOSES -> v.put("periodicity", BeneficiaryTerms.periodicity((String) r.get("periodicity")));
            default -> { }
        }
        v.put("id", r.get("_id"));
        return v;
    }

    private Map<Long, DetailChild> children(DetailQuery.Set set, List<Map<String, Object>> raw, List<Long> ids, CurrentUser me) {
        if (ids.isEmpty()) return Map.of();
        Long[] idArray = ids.toArray(Long[]::new);
        return switch (set) {
            case ORGANISATIONS -> {
                Map<Long, DetailChild> out = new HashMap<>();
                Set<Long> beneficiaries = raw.stream().filter(r -> Set.of("SCHOOL", "NGO").contains(r.get("_type")))
                        .map(r -> ((Number) r.get("_id")).longValue()).collect(Collectors.toSet());
                group(details.needsOf(idArray), "owner")
                        .forEach((org, rows) -> { if (beneficiaries.contains(org)) out.put(org, new DetailChild("Causes and projects", CHILD_NEEDS, rows)); });
                group(details.donationsBy(idArray), "owner")
                        .forEach((org, rows) -> { if (!beneficiaries.contains(org)) out.put(org, new DetailChild("Donations made", CHILD_DONATIONS, rows)); });
                yield out;
            }
            case NEEDS -> group(details.donationsTo(idArray), "owner")
                    .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new DetailChild("Donations received", CHILD_DONATIONS, e.getValue())));
            case PURPOSES -> group(details.matchesOf(idArray), "owner")
                    .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new DetailChild("Confirmed matches", CHILD_MATCHES, e.getValue())));
            case DONATIONS -> Map.of();
        };
    }

    private static Map<Long, List<Map<String, Object>>> group(List<Map<String, Object>> rows, String key) {
        Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            long owner = ((Number) r.remove(key)).longValue();
            out.computeIfAbsent(owner, k -> new ArrayList<>()).add(r);
        }
        return out;
    }

}
