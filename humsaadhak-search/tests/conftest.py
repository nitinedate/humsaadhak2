import pytest
from fastapi.testclient import TestClient

from app.config.settings import Settings
from app.model.card import Card
from app.main import create_app
from app.ml.base import HashingEmbedder, OverlapReranker
from app.util.calibration import Calibrator
from app.service.search_service import SearchService
from app.repository.memory_store import InMemoryStore

NEEDS = [
    (1, "CAUSE", "Smart classroom kit, ZP School Khed", "Education",
     "Two interactive panels and a projector for classes 5 to 7 in a rural zilla parishad school, with teacher training on digital lessons."),
    (2, "PROJECT", "Digital literacy lab for adolescent girls", "Education",
     "A 20-seat computer lab used after school hours by 150 girls aged 12 to 16, teaching typing, internet safety and basic coding."),
    (3, "CAUSE", "Toilet block for girls' hostel", "Health and sanitation",
     "Six toilets and two bathing rooms for 140 resident girls in a tribal residential school."),
    (4, "PROJECT", "Millet snack programme for anganwadis", "Nutrition",
     "Locally made millet laddoos for children under six in four anganwadi centres, with monthly growth monitoring."),
    (5, "PROJECT", "Sports equipment drive", "Sports",
     "Kits for kabaddi, kho-kho and athletics in ten schools."),
]
PURPOSE = (100, "Digital classrooms for rural schools", "Education",
           "Fund smart classrooms and computer labs in government and zilla parishad schools in rural Maharashtra, "
           "classes 5 to 10, including teacher training and two years of maintenance.")


def settings():
    return Settings(service_token="test-token", database_url=None, embedding_model="fake", reranker_model="fake",
                    embedding_dim=512, retrieve_k=100, rerank_k=50, sector_bonus=0.5, calibration_path=None,
                    use_fake_models=True)


@pytest.fixture
def pipeline():
    p = SearchService(InMemoryStore(), HashingEmbedder(512), OverlapReranker(), Calibrator())
    for i, kind, name, sector, desc in NEEDS:
        p.index(Card(entity_type="need", entity_id=i, kind=kind, name=name, description=desc, sector=sector, amount="450000"))
    pid, name, sector, desc = PURPOSE
    p.index(Card(entity_type="purpose", entity_id=pid, kind="PURPOSE", name=name, description=desc, sector=sector,
                 amount="500000", periodicity="YEARLY"))
    return p


@pytest.fixture
def client(pipeline):
    return TestClient(create_app(settings(), pipeline))


AUTH = {"Authorization": "Bearer test-token"}
