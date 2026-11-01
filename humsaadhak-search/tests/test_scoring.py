"""Match percentages that tell a good match from the best one, domain terms explained, and an index kept current."""
from app.ml.base import HashingEmbedder
from app.model.card import Card
from app.repository.memory_store import InMemoryStore
from app.service.index_service import IndexService
from app.service.search_service import SearchService
from app.util.calibration import Calibrator
from app.util.explain import explain
from app.util.glossary import notes_for
from app.util.scoring import relevance, sigmoid


class FixedReranker:
    """Log-odds chosen per need, like a real reranker that finds both needs plausible but one clearly better."""
    name = "fixed"

    def __init__(self, by_word: dict[str, float]):
        self.by_word = by_word

    def score(self, query, documents, instruction):
        return [next((z for w, z in self.by_word.items() if w in d.lower()), -6.0) for d in documents]


def card(i, kind, name, desc, sector="Education", entity="need"):
    return Card(entity_type=entity, entity_id=i, kind=kind, name=name, description=desc, sector=sector)


PURPOSE = card(100, "PURPOSE", "Need to provide Books to poor students",
               "I am interested to provide my fund to poor students to provide books.", entity="purpose")
LIBRARY = card(1, "CAUSE", "Need to construct library", "We require fund to construct the library.")
BOOKS = card(2, "PROJECT", "Need to provide Books to poor children", "We need the funding to provide books for education of poor children.")


def pipeline(reranker, temperature=3.0):
    p = SearchService(InMemoryStore(), HashingEmbedder(256), reranker, Calibrator(), temperature=temperature)
    for c in (LIBRARY, BOOKS, PURPOSE):
        p.index(c)
    return p


def test_plausible_matches_no_longer_all_show_100():
    # the reranker leans "yes" for both (sigmoid: 0.97 and 0.9997): read as probabilities, both became 100
    p = pipeline(FixedReranker({"construct": 3.5, "books for": 8.0}))
    hits = p.search(PURPOSE, "need", "purpose->need", 10)
    by_id = {h.entity_id: h for h in hits}
    assert [h.entity_id for h in hits][:2] == [2, 1]                       # books first, then the library
    assert by_id[2].score > by_id[1].score + 10                             # clearly apart
    assert all(h.score < 100 for h in hits)
    assert by_id[2].rerank_score > 0.99 and by_id[1].rerank_score > 0.95  # the model's own probabilities, kept for the record


def test_relevance_spreads_verdicts_and_never_passes_100():
    assert round(relevance(9, False, 0.5, 3.0), 2) == 0.95
    assert round(relevance(3, False, 0.5, 3.0), 2) == 0.73
    assert relevance(0, False, 0.5, 3.0) == 0.5                             # undecided
    assert relevance(-3, False, 0.5, 3.0) < 0.5
    values = [relevance(z, True, 0.5, 3.0) for z in (6, 10, 14, 30)]
    assert values == sorted(values) and len(set(values)) == 4 and max(values) < 1.0
    assert relevance(3, True, 0.5, 3.0) > relevance(3, False, 0.5, 3.0)     # same sector: a little higher
    assert abs(sigmoid(-800)) < 1e-12 and sigmoid(800) == 1.0               # no overflow either way


def test_the_live_search_and_the_calibration_tool_score_alike():
    p = pipeline(FixedReranker({"construct": 3.5, "books for": 8.0}))
    for h in p.search(PURPOSE, "need", "purpose->need", 10):
        need = p.store.get("need", h.entity_id).card
        assert h.score == p.calibrator(p.relevance(PURPOSE, need, "purpose->need"))


def test_local_terms_are_explained_for_the_models():
    c = card(3, "CAUSE", "Smart classroom, ZP School Khed", "For classes 5 to 7 in a KGBV hostel, CSR under Schedule VII.")
    text = c.text()
    assert "zp school: a government primary school run by the zilla parishad" in text
    assert "kgbv: Kasturba Gandhi Balika Vidyalaya" in text and "schedule vii:" in text
    assert notes_for("an ashram shala") == ["ashram shala: a residential government school for tribal (Adivasi) children"]
    assert notes_for("stems of flowers in a trust") == []                   # ordinary words are left alone
    assert "Terms used" not in BOOKS.text()                                 # nothing to explain


def test_the_index_follows_changes_of_wording_or_model():
    p = pipeline(FixedReranker({}))
    indexing = IndexService(p)
    assert indexing.refresh_stale() == 0                                    # everything current
    p.embedder.name = "fine-tuned-embedding"                                # a new model: every vector is stale
    assert indexing.refresh_stale() == 3
    assert indexing.refresh_stale() == 0
    assert not p.index(BOOKS) and p.index(BOOKS.model_copy(update={"description": "Books for 200 children."}))


def test_reasons_skip_generic_words_and_match_singular_with_plural():
    p = pipeline(FixedReranker({}))
    qvec = p.embedder.embed_query(PURPOSE.text(), "")
    why = explain(PURPOSE, card(9, "CAUSE", "A book for every child", "Story book sets for poor children."), p.embedder, qvec)
    terms = why["sharedTerms"].split(", ")
    assert "books" in terms and "poor" in terms
    assert not {"need", "provide", "fund"} & set(terms)
