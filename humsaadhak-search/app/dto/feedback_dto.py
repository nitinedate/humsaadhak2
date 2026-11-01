"""A reviewer decision or donation sent back as a training label."""
from pydantic import Field

from app.dto.match_dto import Camel


class LabelIn(Camel):
    purpose_id: int
    need_id: int
    label: str = Field(pattern="^(positive|negative)$")
    source: str = "review"
