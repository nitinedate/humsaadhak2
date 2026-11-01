"""How accurate the matching is, on donor purposes and causes/projects judged by hand (training/golden_set.py).

Usage (in the search container, with the real models): python -m training.golden_eval
    deploy_local.cmd match-eval   (or deploy/deploy.sh match-eval) runs it for you.
It builds a separate in-memory index, so your own data is not read or changed, runs every purpose through the same
pipeline the app uses, and reports how often the engine gets it right.
"""
from dataclasses import dataclass, field

from app.model.card import Card
from app.repository.memory_store import InMemoryStore
from app.service.search_service import SearchService
from training.golden_set import NEEDS, PURPOSES


@dataclass
class Report:
    purposes: int
    top_is_best: float           # the top match is a best match
    top_is_good: float           # the top match is a best or good match
    best_in_top3: float          # a best match is among the top 3
    best_above_unrelated: float  # of all (best, unrelated) pairs, how many are ranked the right way round
    pairs: int
    average_score: dict = field(default_factory=dict)   # best / good / unrelated -> average match %
    rows: list = field(default_factory=list)            # (purpose, top need, top score, grade of top, rank of first best)


def evaluate_golden(search: SearchService) -> Report:
    for i, (kind, name, sector, desc) in NEEDS.items():
        search.index(Card(entity_type="need", entity_id=i, kind=kind, name=name, description=desc, sector=sector))
    top_best = top_good = best3 = right = pairs = 0
    scores = {"best": [], "good": [], "unrelated": []}
    rows = []
    for n, (name, sector, desc, grades) in enumerate(PURPOSES, start=1):
        purpose = Card(entity_type="purpose", entity_id=10_000 + n, kind="PURPOSE", name=name, description=desc, sector=sector)
        hits = search.search(purpose, "need", "purpose->need", len(NEEDS))
        rank = {h.entity_id: h.rank for h in hits}
        grade = lambda need_id: grades.get(need_id, 0)
        top = hits[0]
        top_best += grade(top.entity_id) == 2
        top_good += grade(top.entity_id) >= 1
        best = [i for i, g in grades.items() if g == 2]
        first_best = min(rank.get(i, len(NEEDS) + 1) for i in best)
        best3 += first_best <= 3
        unrelated = [i for i in NEEDS if grade(i) == 0]
        for b in best:
            for u in unrelated:
                pairs += 1
                right += rank.get(b, len(NEEDS) + 1) < rank.get(u, len(NEEDS) + 1)
        for h in hits:
            scores["best" if grade(h.entity_id) == 2 else "good" if grade(h.entity_id) == 1 else "unrelated"].append(h.score)
        rows.append((name, NEEDS[top.entity_id][1], top.score, grade(top.entity_id), first_best))
    k = len(PURPOSES)
    return Report(k, top_best / k, top_good / k, best3 / k, right / pairs if pairs else 0.0, pairs,
                  {g: (round(sum(v) / len(v)) if v else None) for g, v in scores.items()}, rows)


def render(report: Report, models: str) -> str:
    pct = lambda x: f"{round(100 * x)}%"
    lines = [f"Matching accuracy on {report.purposes} donor purposes and {len(NEEDS)} causes and projects, judged by hand",
             f"Models: {models}", "",
             f"  Top match is a best match ............... {pct(report.top_is_best)}  ({round(report.top_is_best * report.purposes)} of {report.purposes})",
             f"  Top match is a best or good match ....... {pct(report.top_is_good)}",
             f"  A best match among the top 3 ............ {pct(report.best_in_top3)}",
             f"  Best matches ranked above unrelated ones  {pct(report.best_above_unrelated)}  (of {report.pairs} pairs)",
             f"  Average match: best {report.average_score['best']}%, good {report.average_score['good']}%, "
             f"unrelated {report.average_score['unrelated']}%", "", "Purpose by purpose (top match, its score):"]
    for name, top, score, grade, first_best in report.rows:
        mark = {2: "best", 1: "good", 0: "WRONG"}[grade]
        lines.append(f"  {mark:5}  {name} -> {top} ({score}%)" + ("" if grade == 2 else f"; first best match ranked {first_best}"))
    return "\n".join(lines)


if __name__ == "__main__":
    from app.config.settings import Settings
    from app.main import build_models
    from app.util.calibration import Calibrator
    settings = Settings.from_env()
    embedder, reranker = build_models(settings)
    search = SearchService(InMemoryStore(), embedder, reranker, Calibrator.load(settings.calibration_path),
                           settings.retrieve_k, settings.rerank_k, settings.sector_bonus, settings.rerank_temperature)
    print("Loading done; matching ...", flush=True)
    print(render(evaluate_golden(search), search.model_version))
