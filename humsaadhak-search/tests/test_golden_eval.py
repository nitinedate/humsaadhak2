"""The accuracy report: correct arithmetic (an oracle scores 100%), and it runs end to end on the stand-in models."""
from app.ml.base import HashingEmbedder, OverlapReranker
from app.repository.memory_store import InMemoryStore
from app.service.search_service import SearchService
from app.util.calibration import Calibrator
from training.golden_eval import evaluate_golden, render
from training.golden_set import NEEDS, PURPOSES
from training.fine_tune import training_examples
from app.model.card import Card
from app.model.label import Label


class OracleReranker:
    """Knows the hand-made grades: best 8, good 3, unrelated -5 (log-odds)."""
    name = "oracle"

    def score(self, query, documents, instruction):
        grades = next(g for name, _, _, g in PURPOSES if f"Donor purpose: {name}." in query)
        out = []
        for d in documents:
            need = next(i for i, (_, name, _, _) in NEEDS.items() if f": {name}." in d)
            out.append({2: 8.0, 1: 3.0}.get(grades.get(need, 0), -5.0))
        return out


def search(reranker):
    return SearchService(InMemoryStore(), HashingEmbedder(256), reranker, Calibrator())


def test_an_oracle_scores_100_percent_so_the_measures_are_computed_right():
    r = evaluate_golden(search(OracleReranker()))
    assert (r.top_is_best, r.top_is_good, r.best_in_top3, r.best_above_unrelated) == (1.0, 1.0, 1.0, 1.0)
    assert r.average_score["best"] > r.average_score["good"] > r.average_score["unrelated"]
    assert r.purposes == len(PURPOSES) == 16 and r.pairs > 300


def test_the_report_runs_on_the_stand_in_models_and_reads_well():
    r = evaluate_golden(search(OverlapReranker()))
    for v in (r.top_is_best, r.top_is_good, r.best_in_top3, r.best_above_unrelated):
        assert 0.0 <= v <= 1.0
    text = render(r, "stand-in models")
    assert "Top match is a best match" in text and "Reading material for underprivileged kids ->" in text
    assert len(text.splitlines()) == 10 + len(PURPOSES)


def test_every_graded_need_exists_and_every_purpose_has_a_best_match():
    for _, _, _, grades in PURPOSES:
        assert set(grades) <= set(NEEDS) and 2 in grades.values()


def test_fine_tuning_learns_from_approved_pairs_only():
    store = InMemoryStore()
    p = SearchService(store, HashingEmbedder(64), OverlapReranker(), Calibrator())
    p.index(Card(entity_type="purpose", entity_id=1, kind="PURPOSE", name="Books", description="Books for poor students.", sector="Education"))
    for i in (10, 11):
        p.index(Card(entity_type="need", entity_id=i, kind="CAUSE", name=f"Need {i}", description="Library books.", sector="Education"))
    store.add_labels([Label(1, 10, "positive", "review"), Label(1, 11, "negative", "review"), Label(1, 10, "positive", "donation")])
    examples = training_examples(store)
    assert len(examples) == 1                                                  # approved only, each pair once
    query, need = examples[0]
    assert query.startswith("Instruct: Given a donor's funding purpose") and "Donor purpose: Books." in query
    assert need.startswith("School cause: Need 10.")
