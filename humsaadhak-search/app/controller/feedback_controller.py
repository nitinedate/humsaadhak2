"""Reviewer decisions and donations sent back as training labels."""
from fastapi import APIRouter, Depends

from app.controller.dependencies import feedback_service, require_token
from app.dto.feedback_dto import LabelIn
from app.service.feedback_service import FeedbackService

router = APIRouter(prefix="/v1", tags=["feedback"], dependencies=[Depends(require_token)])


@router.post("/feedback", status_code=202)
def feedback(labels: list[LabelIn], feedback: FeedbackService = Depends(feedback_service)):
    return {"stored": feedback.store(labels)}
