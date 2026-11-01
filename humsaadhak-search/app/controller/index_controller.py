"""Index maintenance, called by the Java backend when purposes and needs are approved, changed or removed."""
from fastapi import APIRouter, Depends

from app.controller.dependencies import index_service, require_token
from app.model.card import Card
from app.service.index_service import IndexService

router = APIRouter(prefix="/v1", tags=["index"], dependencies=[Depends(require_token)])


@router.post("/index/upsert", status_code=202)
def upsert(card: Card, indexing: IndexService = Depends(index_service)):
    return {"reindexed": indexing.upsert(card)}


@router.delete("/index/{entity_type}/{entity_id}", status_code=204)
def delete(entity_type: str, entity_id: int, indexing: IndexService = Depends(index_service)):
    indexing.delete(entity_type, entity_id)


@router.post("/admin/reindex")
def reindex(indexing: IndexService = Depends(index_service)):
    """Re-embeds everything with the current model (after a model upgrade)."""
    return {"reindexed": indexing.reindex_all()}
