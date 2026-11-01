"""A reviewer's or donor's judgement on a purpose-need pair: the training data for calibration and evaluation."""
from dataclasses import dataclass, field


@dataclass
class Label:
    purpose_id: int
    need_id: int
    label: str      # positive | negative
    source: str     # review | donation | manual
    extra: dict = field(default_factory=dict)
