"""A purpose or need as the search models read it: the text, plus a hash to skip unchanged records."""
import hashlib

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from app.util.glossary import notes_for


class Card(BaseModel):
    """Same shape as SearchClient.Card on the Java side (camelCase on the wire)."""
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    entity_type: str            # "purpose" | "need"
    entity_id: int
    kind: str                   # PURPOSE | CAUSE | PROJECT
    name: str
    description: str
    sector: str
    amount: str | None = None
    periodicity: str | None = None

    def text(self) -> str:
        label = {"CAUSE": "School cause", "PROJECT": "NGO project", "PURPOSE": "Donor purpose"}.get(self.kind, self.kind)
        parts = [f"{label}: {self.name}.", f"Sector: {self.sector}.", self.description.strip()]
        if self.amount:
            parts.append(f"Amount: Rs {self.amount}.")
        if self.periodicity:
            parts.append(f"Periodicity: {self.periodicity.replace('_', ' ').lower()}.")
        notes = notes_for(f"{self.name} {self.description}")
        if notes:                                     # Indian terms explained for the models (app/util/glossary.py)
            parts.append("Terms used: " + "; ".join(notes) + ".")
        return " ".join(parts)

    def text_hash(self) -> str:
        return hashlib.sha256(self.text().encode("utf-8")).hexdigest()


# One instruction per search direction (Qwen3 models are instruction-aware, and both the embedding and the reranking
# model read it). It says what a good match means on HumSaadhak: the same kind of help, for the same people.
INSTRUCTIONS = {
    "purpose->need": ("Given a donor's funding purpose, find the school causes and NGO projects in India that this donation "
                      "would directly pay for: the same kind of help (for example books, meals, classrooms, scholarships or "
                      "health care) for the same people (for example poor students, girls, tribal children or children "
                      "with disabilities)"),
    "need->purpose": ("Given a school cause or NGO project in India that needs funding, find the donor purposes that would "
                      "directly pay for this kind of help for these people"),
    "free-text": "Given a donor's search for something to fund, find the school causes and NGO projects in India that match it",
}
ENTITY_TYPES = ("purpose", "need")
