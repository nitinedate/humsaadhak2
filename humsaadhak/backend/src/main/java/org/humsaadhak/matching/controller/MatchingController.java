package org.humsaadhak.matching.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import java.util.List;
import org.humsaadhak.matching.dto.CandidateRow;
import org.humsaadhak.matching.dto.Created;
import org.humsaadhak.matching.dto.DecisionsBody;
import org.humsaadhak.matching.dto.MatchRow;
import org.humsaadhak.matching.dto.RunBody;
import org.humsaadhak.matching.dto.SuggestedBenefactor;
import org.humsaadhak.matching.service.MatchingService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Matching runs, reviewing candidates, confirmed matches and suggested donors. */
@RestController
@RequestMapping("/api/v2")
public class MatchingController {
    private final MatchingService matching;

    public MatchingController(MatchingService matching) { this.matching = matching; }

    @AuditAction(value = "match.run", target = AuditTarget.MATCH_RUN, id = "#result.id()", detail = "#body.purposeIds().size() + ' purposes'")
    @PostMapping("/match-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('perm:match.run')")
    public Created run(@Valid @RequestBody RunBody body) { return new Created(matching.run(body.purposeIds(), CurrentUser.get())); }

    @GetMapping("/purposes/{id}/candidates")
    @PreAuthorize("hasAuthority('perm:match.decide')")
    public List<CandidateRow> candidates(@PathVariable long id) { return matching.candidateRows(id); }

    @AuditAction(value = "match.confirm", target = AuditTarget.PURPOSE, id = "#id", detail = "#body.decisions().?[approve()].size() + ' approved, ' + #body.decisions().?[!approve()].size() + ' rejected'")
    @PostMapping("/purposes/{id}/candidates/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('perm:match.decide')")
    public Created confirm(@PathVariable long id, @Valid @RequestBody DecisionsBody body) {
        return new Created(matching.confirm(id, body.decisions(), CurrentUser.get()));
    }

    @GetMapping("/purposes/{id}/matches")
    public List<MatchRow> matchesOfPurpose(@PathVariable long id) { return matching.matchesOfPurpose(id, CurrentUser.get()); }

    @GetMapping("/needs/{id}/suggested-benefactors")
    public List<SuggestedBenefactor> suggestions(@PathVariable long id, @RequestParam(defaultValue = "10") int topK) {
        return matching.suggestions(id, topK, CurrentUser.get());
    }
}
