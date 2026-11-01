"""Ranked matches with calibrated 0-100 scores and the reasons shown next to each score."""
from fastapi import APIRouter, Depends

from app.controller.dependencies import match_service, require_token
from app.dto.match_dto import FreeTextRequest, MatchRequest, MatchResponse
from app.service.match_service import MatchService

router = APIRouter(prefix="/v1", tags=["match"], dependencies=[Depends(require_token)])


@router.post("/match/purpose-to-needs", response_model=MatchResponse, response_model_by_alias=True)
def purpose_to_needs(req: MatchRequest, matching: MatchService = Depends(match_service)):
    return matching.purpose_to_needs(req)


@router.post("/match/need-to-purposes", response_model=MatchResponse, response_model_by_alias=True)
def need_to_purposes(req: MatchRequest, matching: MatchService = Depends(match_service)):
    return matching.need_to_purposes(req)


@router.post("/search", response_model=MatchResponse, response_model_by_alias=True)
def free_text(req: FreeTextRequest, matching: MatchService = Depends(match_service)):
    return matching.free_text(req)
