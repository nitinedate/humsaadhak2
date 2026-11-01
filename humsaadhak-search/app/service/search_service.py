"""The four-stage pipeline: filter -> retrieve (dense + keyword, fused) -> rerank -> calibrate, with explanations."""
import hashlib

from app.ml.base import Embedder, Reranker
from app.model.card import INSTRUCTIONS, Card
from app.model.hit import Hit
from app.repository.vector_store import VectorStore
from app.util.calibration import Calibrator
from app.util.explain import explain
from app.util.fusion import rrf
from app.util.scoring import relevance, sigmoid


class SearchService:
    def __init__(self, store: VectorStore, embedder: Embedder, reranker: Reranker, calibrator: Calibrator,
                 retrieve_k: int = 100, rerank_k: int = 50, sector_bonus: float = 0.5, temperature: float = 3.0):
        self.store, self.embedder, self.reranker, self.calibrator = store, embedder, reranker, calibrator
        self.retrieve_k, self.rerank_k, self.sector_bonus, self.temperature = retrieve_k, rerank_k, sector_bonus, temperature

    @property
    def model_version(self) -> str:
        return f"{self.embedder.name}|{self.reranker.name}|T{self.temperature:g}|{self.calibrator.version}"

    def index_hash(self, card: Card) -> str:
        """What a stored vector was made from: the card's text and the embedding model. When either changes (a reworded
        card, the glossary, a fine-tuned model), the record is re-embedded."""
        return hashlib.sha256(f"{self.embedder.name}\n{card.text()}".encode("utf-8")).hexdigest()

    def relevance(self, query: Card, doc: Card, direction: str) -> float:
        """The same 0-1 relevance the search uses, for one pair (training/fit_calibration.py fits on it)."""
        z = self.reranker.score(query.text(), [doc.text()], INSTRUCTIONS[direction])[0]
        return relevance(z, doc.sector == query.sector, self.sector_bonus, self.temperature)

    def index(self, card: Card) -> bool:
        """Embeds and stores a card. Returns False when neither its text nor the model changed since the last index."""
        h = self.index_hash(card)
        existing = self.store.get(card.entity_type, card.entity_id)
        if existing and existing.text_hash == h:
            return False
        self.store.upsert(card, self.embedder.embed_documents([card.text()])[0], h)
        return True

    def search(self, query: Card, target_type: str, direction: str, top_k: int,
               sector_filter: str | None = None, exclude_ids: set[int] | None = None) -> list[Hit]:
        instruction = INSTRUCTIONS[direction]
        qtext = query.text()
        qvec = self.embedder.embed_query(qtext, instruction)

        dense = self.store.dense_search(target_type, qvec, self.retrieve_k, sector_filter)
        keyword = self.store.keyword_search(target_type, qtext, self.retrieve_k, sector_filter)
        dense_score = dict(dense)
        fused = rrf([[i for i, _ in dense], [i for i, _ in keyword]])
        candidates = [i for i, _ in fused if not exclude_ids or i not in exclude_ids][: self.rerank_k]
        if not candidates:
            return []

        docs = [self.store.get(target_type, i) for i in candidates]
        pairs = [(i, d) for i, d in zip(candidates, docs) if d is not None]
        log_odds = self.reranker.score(qtext, [d.card.text() for _, d in pairs], instruction)

        scored = []
        for (i, d), z in zip(pairs, log_odds):
            rel = relevance(z, d.card.sector == query.sector, self.sector_bonus, self.temperature)
            scored.append((i, d, z, rel))
        # best first; equal relevance: the closer meaning (dense similarity) first, then the lower id
        scored.sort(key=lambda x: (-x[3], -dense_score.get(x[0], 0.0), x[0]))

        hits = []
        for rank, (i, d, z, rel) in enumerate(scored[:top_k], start=1):
            hits.append(Hit(entity_id=i, rank=rank, retrieval_score=round(dense_score.get(i, 0.0), 4),
                            rerank_score=round(sigmoid(z), 4), score=self.calibrator(rel),
                            explanation=explain(query, d.card, self.embedder, qvec)))
        return hits
