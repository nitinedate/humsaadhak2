package org.humsaadhak.matching.service;

import org.humsaadhak.matching.util.RerunPlan;
import org.springframework.security.access.AccessDeniedException;
import org.humsaadhak.matching.dto.SuggestedBenefactor;
import org.humsaadhak.matching.dto.MatchRow;
import org.humsaadhak.matching.dto.CandidateRow;
import org.humsaadhak.matching.util.CandidateDecisionValidator;
import org.humsaadhak.matching.entity.Match;
import org.humsaadhak.matching.entity.MatchBatch;
import org.humsaadhak.matching.repository.MatchBatchRepository;
import org.humsaadhak.matching.entity.MatchCandidate;
import org.humsaadhak.matching.repository.MatchCandidateRepository;
import org.humsaadhak.matching.repository.MatchRepository;
import org.humsaadhak.matching.entity.MatchRun;
import org.humsaadhak.matching.repository.MatchRunRepository;
import org.humsaadhak.matching.client.SearchClient;

import java.util.*;
import java.util.stream.Collectors;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.catalogue.api.CatalogueEvents.NeedIndexable;
import org.humsaadhak.catalogue.api.CatalogueEvents.PurposeIndexable;
import org.humsaadhak.matching.api.Matches;
import org.humsaadhak.matching.api.MatchesConfirmed;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.util.RefNo;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingService implements Matches {
    private final MatchRunRepository runs;
    private final MatchCandidateRepository candidates;
    private final MatchBatchRepository batches;
    private final MatchRepository matches;
    private final SearchClient search;
    private final Catalogue catalogue;
    private final ApplicationEventPublisher events;
    private final int storeThreshold;
    private final int showThreshold;
    private final int topK;

    public MatchingService(MatchRunRepository runs, MatchCandidateRepository candidates, MatchBatchRepository batches,
                    MatchRepository matches, SearchClient search, Catalogue catalogue, ApplicationEventPublisher events,
                    @Value("${humsaadhak.matching.store-threshold:40}") int storeThreshold,
                    @Value("${humsaadhak.matching.show-threshold:50}") int showThreshold,
                    @Value("${humsaadhak.matching.top-k:25}") int topK) {
        this.runs = runs; this.candidates = candidates; this.batches = batches; this.matches = matches;
        this.search = search; this.catalogue = catalogue; this.events = events;
        this.storeThreshold = storeThreshold; this.showThreshold = showThreshold; this.topK = topK;
    }

    /** Runs the engine for approved purposes; stores new candidates at or above the store threshold. */
    @Transactional
    public long run(List<Long> purposeIds, CurrentUser me) {
        if (purposeIds == null || purposeIds.isEmpty()) throw new DomainException("no-purposes", "Choose at least one purpose.");
        MatchRun run = runs.save(new MatchRun(me.id(), purposeIds.size()));
        int stored = 0;
        for (long purposeId : purposeIds) {
            var purpose = catalogue.purpose(purposeId).orElseThrow(() -> new NotFoundException("Purpose", purposeId));
            if (!purpose.approved()) throw new DomainException("purpose-not-approved", purpose.refNo() + " is not approved yet.");
            SearchClient.MatchResponse res = search.purposeToNeeds(purposeId, topK, Map.of());
            run.setModelVersion(res.modelVersion());
            Map<Long, MatchCandidate> existing = new HashMap<>();
            candidates.findByPurposeId(purposeId).forEach(c -> existing.put(c.getNeedId(), c));
            Map<Long, Boolean> waiting = new HashMap<>();
            existing.forEach((needId, c) -> waiting.put(needId, c.getDecision() == MatchCandidate.Decision.PENDING));
            Map<Long, SearchClient.Hit> hits = new HashMap<>();
            res.hits().forEach(h -> hits.putIfAbsent(h.entityId(), h));
            List<RerunPlan.Hit> planned = res.hits().stream().map(h -> new RerunPlan.Hit(h.entityId(), h.score())).toList();
            for (RerunPlan.Step step : RerunPlan.plan(waiting, planned, storeThreshold)) {
                SearchClient.Hit h = hits.get(step.needId());
                MatchCandidate c = existing.get(step.needId());
                switch (step.action()) {
                    case CREATE -> {
                        candidates.save(new MatchCandidate(run.getId(), purposeId, h.entityId(), h.retrievalScore(), h.rerankScore(),
                                h.score(), h.rank(), res.modelVersion(), toJson(h.explanation())));
                        stored++;
                    }
                    case RESCORE -> c.rescore(run.getId(), h.retrievalScore(), h.rerankScore(), h.score(), h.rank(), res.modelVersion(),
                            toJson(h.explanation()));
                    case HIDE -> c.rescore(run.getId(), c.getRetrievalScore(), c.getRerankScore(), 0, c.getEngineRank(), res.modelVersion(),
                            c.getExplanationJson());
                    case KEEP -> { }
                }
            }
        }
        run.setCandidateCount(stored);
        return run.getId();
    }

    /** Candidates still waiting for review: approved ones are confirmed matches, rejected ones training labels. */
    @Transactional(readOnly = true)
    public List<MatchCandidate> candidatesToReview(long purposeId) {
        return candidates.findByPurposeIdAndFinalScoreGreaterThanEqualOrderByEngineRankAsc(purposeId, showThreshold).stream()
                .filter(c -> c.getDecision() == MatchCandidate.Decision.PENDING).toList();
    }

    /** All-or-nothing confirmation of a purpose's shown candidates. */
    @Transactional
    public long confirm(long purposeId, List<CandidateDecisionValidator.Decision> decisions, CurrentUser me) {
        var purpose = catalogue.purpose(purposeId).orElseThrow(() -> new NotFoundException("Purpose", purposeId));
        List<MatchCandidate> shown = candidatesToReview(purposeId);
        Set<Long> alreadyDecided = candidates.findByPurposeId(purposeId).stream()
                .filter(c -> c.getDecision() != MatchCandidate.Decision.PENDING).map(MatchCandidate::getId).collect(Collectors.toSet());
        CandidateDecisionValidator.validate(shown.stream().map(MatchCandidate::getId).collect(Collectors.toSet()), alreadyDecided, decisions);

        MatchBatch batch = batches.save(new MatchBatch(purposeId, me.id()));
        batch.setRefNo(RefNo.of(RefNo.MATCH_BATCH, batch.getId()));
        Map<Long, MatchCandidate> byId = shown.stream().collect(Collectors.toMap(MatchCandidate::getId, c -> c));
        List<Long> matchIds = new ArrayList<>();
        List<SearchClient.Label> labels = new ArrayList<>();
        decisions.stream().sorted(Comparator.comparing(d -> d.priority() == null ? Integer.MAX_VALUE : d.priority())).forEach(d -> {
            MatchCandidate c = byId.get(d.candidateId());
            c.setDecision(d.approve() ? MatchCandidate.Decision.APPROVED : MatchCandidate.Decision.REJECTED);
            c.setRemark(d.remark());
            c.setPriority(d.priority());
            labels.add(new SearchClient.Label(purposeId, c.getNeedId(), d.approve() ? "positive" : "negative", "review"));
            if (d.approve()) {
                Match m = matches.save(new Match(batch.getId(), c.getId(), purposeId, c.getNeedId(), purpose.donorOrgId(), d.priority()));
                m.setRefNo(RefNo.of(RefNo.MATCH, m.getId()));
                matchIds.add(m.getId());
            }
        });
        events.publishEvent(new MatchesConfirmed(batch.getId(), batch.getRefNo(), purposeId, purpose.donorOrgId(), matchIds));
        events.publishEvent(new LabelsCaptured(labels));
        return batch.getId();
    }

    public record LabelsCaptured(List<SearchClient.Label> labels) {}

    @ApplicationModuleListener
    public void on(LabelsCaptured e) { search.sendLabels(e.labels()); }

    /** Keeps the search index in step with approvals and funding (after commit, retried on failure). */
    @ApplicationModuleListener
    public void on(PurposeIndexable e) {
        if (!e.indexable()) { search.delete("purpose", e.purposeId()); return; }
        catalogue.purpose(e.purposeId()).ifPresent(p -> search.upsert(new SearchClient.Card("purpose", p.id(), "PURPOSE",
                p.name(), p.description(), p.sector(), String.valueOf(p.commitmentAmount()), p.periodicity().name())));
    }

    @ApplicationModuleListener
    public void on(NeedIndexable e) {
        if (!e.indexable()) { search.delete("need", e.needId()); return; }
        catalogue.need(e.needId()).ifPresent(n -> search.upsert(new SearchClient.Card("need", n.id(), n.kind().name(),
                n.name(), n.description(), n.sector(), String.valueOf(n.remaining()), n.periodicity().name())));
    }

    @Override @Transactional(readOnly = true)
    public boolean donorHasConfirmedMatch(long donorOrgId, long needId) { return matches.existsByDonorOrgIdAndNeedId(donorOrgId, needId); }

    @Override @Transactional(readOnly = true)
    public Optional<MatchView> match(long matchId) {
        return matches.findById(matchId).map(m -> new MatchView(m.getId(), m.getRefNo(), m.getPurposeId(), m.getNeedId(), m.getDonorOrgId(), m.getPriority()));
    }

    private static String toJson(Map<String, Object> m) {
        if (m == null) return "{}";
        // Small, flat explanation maps only; full JSON mapping happens in the API layer.
        return m.entrySet().stream().map(e -> "\"" + e.getKey().replace("\"", "") + "\":\"" + String.valueOf(e.getValue()).replace("\"", "'") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    /** Candidates still to review for a purpose, with what the screens show about each cause or project. */
    @Transactional(readOnly = true)
    public List<CandidateRow> candidateRows(long purposeId) {
        return candidatesToReview(purposeId).stream().map(this::row).toList();
    }

    /** A purpose's decided candidates: approved ones in the donor's priority order, then rejected ones, newest first. */
    @Transactional(readOnly = true)
    public List<CandidateRow> decidedRows(long purposeId) {
        List<MatchCandidate> decided = candidates.findByPurposeId(purposeId).stream()
                .filter(c -> c.getDecision() != MatchCandidate.Decision.PENDING).toList();
        List<CandidateRow> rows = new ArrayList<>();
        decided.stream().filter(c -> c.getDecision() == MatchCandidate.Decision.APPROVED)
                .sorted(Comparator.comparing((MatchCandidate c) -> c.getPriority() == null ? Integer.MAX_VALUE : c.getPriority()))
                .forEach(c -> rows.add(row(c)));
        decided.stream().filter(c -> c.getDecision() == MatchCandidate.Decision.REJECTED)
                .sorted(Comparator.comparing(MatchCandidate::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(c -> rows.add(row(c)));
        return rows;
    }

    private CandidateRow row(MatchCandidate c) {
        var n = catalogue.need(c.getNeedId()).orElseThrow();
        boolean decided = c.getDecision() != MatchCandidate.Decision.PENDING;
        return new CandidateRow(c.getId(), c.getNeedId(), n.name(), n.kind().name(), n.sector(), n.remaining(), c.getFinalScore(),
                c.getEngineRank(), c.getExplanationJson(), c.getDecision().name(), c.getRemark(), c.getPriority(),
                decided ? c.getUpdatedBy() : null, decided ? c.getUpdatedAt() : null);
    }

    /** Confirmed matches of a purpose in priority order; donors see only their own. */
    @Transactional(readOnly = true)
    public List<MatchRow> matchesOfPurpose(long purposeId, CurrentUser me) {
        return matches.findByPurposeIdOrderByPriorityAsc(purposeId).stream()
                .filter(m -> me.role().isStaff() || (me.orgId() != null && me.orgId() == m.getDonorOrgId()))
                .map(m -> {
                    var n = catalogue.need(m.getNeedId()).orElseThrow();
                    return new MatchRow(m.getId(), m.getRefNo(), m.getNeedId(), n.name(), n.kind().name(), n.remaining(), m.getPriority());
                }).toList();
    }

    /** Donors whose approved purposes fit a cause or project (staff, or the school or NGO that owns it). */
    @Transactional(readOnly = true)
    public List<SuggestedBenefactor> suggestions(long needId, int topK, CurrentUser me) {
        var need = catalogue.need(needId).orElseThrow(() -> new NotFoundException("Need", needId));
        if (!me.role().isStaff() && (me.orgId() == null || me.orgId() != need.orgId()))
            throw new AccessDeniedException("need " + needId);
        return search.needToPurposes(needId, Math.min(topK, 25)).hits().stream()
                .flatMap(h -> catalogue.purpose(h.entityId()).filter(Catalogue.PurposeView::approved).stream()
                        .map(p -> new SuggestedBenefactor(p.id(), p.name(), p.donorOrgId(), h.score())))
                .toList();
    }
}
