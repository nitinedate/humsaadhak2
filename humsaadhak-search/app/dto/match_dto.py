"""Request and response bodies of the match and search endpoints (camelCase on the wire, like the Java client)."""
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class MatchRequest(Camel):
    query_id: int
    query_type: str
    top_k: int = Field(default=25, ge=1, le=100)
    filters: dict = Field(default_factory=dict)


class FreeTextRequest(Camel):
    q: str = Field(min_length=3, max_length=2000)
    sector: str | None = None
    top_k: int = Field(default=20, ge=1, le=100)


class HitOut(Camel):
    entity_id: int
    rank: int
    retrieval_score: float
    rerank_score: float
    score: int
    explanation: dict


class MatchResponse(Camel):
    model_version: str
    hits: list[HitOut]
