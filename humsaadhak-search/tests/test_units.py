import pytest

from app.model.card import Card
from app.util.calibration import Calibrator
from app.util.fusion import rrf
from training.metrics import ndcg_at_k, precision_above, recall_at_k


def test_rrf_rewards_agreement_between_lists():
    fused = rrf([[1, 2, 3], [2, 1, 4]])
    assert [d for d, _ in fused][:2] in ([1, 2], [2, 1])
    assert dict(fused)[1] == pytest.approx(1 / 61 + 1 / 62)
    assert dict(fused)[4] < dict(fused)[3] or dict(fused)[4] == pytest.approx(1 / 63)


def test_calibrator_identity_and_custom_curve():
    assert Calibrator()(0.734) == 73
    c = Calibrator([0, 0.5, 1], [0, 0.2, 1])
    assert c(0.5) == 20 and c(0.75) == 60 and c(2.0) == 100
    with pytest.raises(ValueError):
        Calibrator([0, 1], [1, 0])


def test_card_text_and_hash_are_stable():
    c = Card(entity_type="need", entity_id=1, kind="CAUSE", name="Library", description="Books for classes 1 to 5.",
             sector="Education", amount="90000")
    assert c.text().startswith("School cause: Library. Sector: Education.")
    assert c.text_hash() == c.model_copy().text_hash()
    assert Card(**{"entityType": "need", "entityId": 2, "kind": "PROJECT", "name": "x", "description": "y", "sector": "z"}).entity_id == 2


def test_metrics():
    assert ndcg_at_k([1, 2, 3], {1}) == 1.0
    assert recall_at_k([5, 6, 1], {1, 2}, 50) == 0.5
    assert precision_above([(1, 80), (2, 60), (3, 40)], {1}, 50) == 0.5
