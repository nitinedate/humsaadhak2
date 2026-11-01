"""Keeping the index in step with approved purposes and needs."""
from app.exception.errors import InvalidRequestError
from app.model.card import ENTITY_TYPES, Card
from app.service.search_service import SearchService


class IndexService:
    def __init__(self, search: SearchService):
        self.search = search

    def upsert(self, card: Card) -> bool:
        """Returns True when the card was (re-)embedded, False when its text had not changed."""
        if card.entity_type not in ENTITY_TYPES:
            raise InvalidRequestError("entityType must be purpose or need")
        return self.search.index(card)

    def delete(self, entity_type: str, entity_id: int) -> None:
        self.search.store.delete(entity_type, entity_id)

    def reindex_all(self) -> int:
        """Re-embeds everything with the current model (after a model upgrade). Returns how many records were embedded."""
        return self._reembed(only_stale=False)

    def refresh_stale(self) -> int:
        """Re-embeds only records whose text or embedding model changed since they were stored (for example after a
        change to the card wording or the glossary, or a fine-tuned model). Runs at start-up. Returns how many."""
        return self._reembed(only_stale=True)

    def _reembed(self, only_stale: bool) -> int:
        store, search = self.search.store, self.search
        n = 0
        for t in ENTITY_TYPES:
            for i in store.all_ids(t):
                d = store.get(t, i)
                if d is None or (only_stale and d.text_hash == search.index_hash(d.card)):
                    continue
                store.upsert(d.card, search.embedder.embed_documents([d.card.text()])[0], search.index_hash(d.card))
                n += 1
        return n
