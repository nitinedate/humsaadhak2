"""GET /v1/health (open, for load balancers) and GET /v1/models (which models are serving)."""
from fastapi import APIRouter, Depends

from app.controller.dependencies import require_token, search_service
from app.service.search_service import SearchService

router = APIRouter(prefix="/v1", tags=["health"])


@router.get("/health")
def health():
    return {"status": "ok"}


@router.get("/models", dependencies=[Depends(require_token)])
def models(search: SearchService = Depends(search_service)):
    return {"modelVersion": search.model_version}
