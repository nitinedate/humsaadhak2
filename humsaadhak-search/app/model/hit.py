"""One search result, keyed by entity id (never by position)."""
from dataclasses import dataclass


@dataclass
class Hit:
    entity_id: int
    rank: int
    retrieval_score: float
    rerank_score: float
    score: int
    explanation: dict
