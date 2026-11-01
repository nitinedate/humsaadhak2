"""In-memory store for tests and small demos. Same behaviour as the pgvector store."""
import math
from collections import Counter

import numpy as np

from app.model.card import Card
from app.model.label import Label
from app.model.stored_doc import StoredDoc
from app.util.text import tokens


class InMemoryStore:
    def __init__(self):
        self.docs: dict[tuple[str, int], StoredDoc] = {}
        self._labels: list[Label] = []

    def upsert(self, card: Card, vector, text_hash):
        self.docs[(card.entity_type, card.entity_id)] = StoredDoc(card, vector, text_hash)

    def delete(self, entity_type, entity_id):
        self.docs.pop((entity_type, entity_id), None)

    def get(self, entity_type, entity_id):
        return self.docs.get((entity_type, entity_id))

    def _of(self, entity_type, sector):
        return [d for (t, _), d in self.docs.items() if t == entity_type and (sector is None or d.card.sector == sector)]

    def dense_search(self, entity_type, query, k, sector=None):
        docs = self._of(entity_type, sector)
        if not docs:
            return []
        m = np.stack([d.vector for d in docs])
        sims = m @ query
        order = np.argsort(-sims)[:k]
        return [(docs[i].card.entity_id, float(sims[i])) for i in order]

    def keyword_search(self, entity_type, query_text, k, sector=None):
        """BM25 over name + description, comparable to PostgreSQL full-text ranking for this purpose."""
        docs = self._of(entity_type, sector)
        q = set(tokens(query_text))
        if not docs or not q:
            return []
        toks = [tokens(d.card.text()) for d in docs]
        avg = sum(len(t) for t in toks) / len(toks)
        df = Counter(w for t in toks for w in set(t))
        n = len(docs)
        scored = []
        for d, t in zip(docs, toks):
            tf = Counter(t)
            s = 0.0
            for w in q:
                if w in tf:
                    idf = math.log(1 + (n - df[w] + 0.5) / (df[w] + 0.5))
                    s += idf * tf[w] * 2.2 / (tf[w] + 1.2 * (0.25 + 0.75 * len(t) / avg))
            if s > 0:
                scored.append((d.card.entity_id, s))
        scored.sort(key=lambda x: -x[1])
        return scored[:k]

    def all_ids(self, entity_type):
        return [i for (t, i) in self.docs if t == entity_type]

    def add_labels(self, labels):
        self._labels.extend(labels)

    def labels(self):
        return list(self._labels)
