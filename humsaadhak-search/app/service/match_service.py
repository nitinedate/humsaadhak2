"""Matching requests from the Java backend: purpose -> needs, need -> purposes, and free-text search."""
from app.dto.match_dto import FreeTextRequest, HitOut, MatchRequest, MatchResponse
from app.exception.errors import NotIndexedError
from app.model.card import Card
from app.model.hit import Hit
from app.service.search_service import SearchService


class MatchService:
    def __init__(self, search: SearchService):
        self.search = search

    def purpose_to_needs(self, req: MatchRequest) -> MatchResponse:
        query = self._indexed("purpose", req.query_id)
        sector = req.filters.get("sector") if req.filters.get("sectorMode") == "filter" else None
        exclude = set(req.filters.get("excludeNeedIds", []))
        return self._response(self.search.search(query, "need", "purpose->need", req.top_k, sector, exclude))

    def need_to_purposes(self, req: MatchRequest) -> MatchResponse:
        query = self._indexed("need", req.query_id)
        return self._response(self.search.search(query, "purpose", "need->purpose", req.top_k))

    def free_text(self, req: FreeTextRequest) -> MatchResponse:
        query = Card(entity_type="query", entity_id=0, kind="PURPOSE", name=req.q, description=req.q, sector=req.sector or "")
        return self._response(self.search.search(query, "need", "free-text", req.top_k, req.sector))

    def _indexed(self, entity_type: str, entity_id: int) -> Card:
        doc = self.search.store.get(entity_type, entity_id)
        if not doc:
            raise NotIndexedError(f"{entity_type} {entity_id} is not indexed; is it approved?")
        return doc.card

    def _response(self, hits: list[Hit]) -> MatchResponse:
        return MatchResponse(model_version=self.search.model_version, hits=[HitOut(**h.__dict__) for h in hits])
