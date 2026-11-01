"""Stores reviewer decisions and donations as labels for calibration and evaluation."""
from app.dto.feedback_dto import LabelIn
from app.model.label import Label
from app.service.search_service import SearchService


class FeedbackService:
    def __init__(self, search: SearchService):
        self.search = search

    def store(self, labels: list[LabelIn]) -> int:
        self.search.store.add_labels([Label(l.purpose_id, l.need_id, l.label, l.source) for l in labels])
        return len(labels)
