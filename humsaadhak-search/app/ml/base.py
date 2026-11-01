"""Model interfaces plus deterministic stand-ins used by tests and by USE_FAKE_MODELS=true."""
import hashlib
from typing import Protocol, Sequence

import numpy as np

from app.util.text import tokens


class Embedder(Protocol):
    name: str
    dim: int
    def embed_documents(self, texts: Sequence[str]) -> np.ndarray: ...
    def embed_query(self, text: str, instruction: str) -> np.ndarray: ...


class Reranker(Protocol):
    """score() returns log-odds per document, logit(yes) - logit(no): above 0 when the document meets the query."""
    name: str
    def score(self, query: str, documents: Sequence[str], instruction: str) -> list[float]: ...


class HashingEmbedder:
    """Bag-of-words hashed into a fixed vector, L2-normalised. Good enough to test the pipeline end to end."""
    name = "fake-hashing-v1"

    def __init__(self, dim: int = 1024):
        self.dim = dim

    def _vec(self, text: str) -> np.ndarray:
        v = np.zeros(self.dim, dtype=np.float32)
        for t in tokens(text):
            h = int(hashlib.md5(t.encode()).hexdigest(), 16)
            v[h % self.dim] += 1.0 if (h >> 64) % 2 == 0 else -1.0
        n = np.linalg.norm(v)
        return v / n if n else v

    def embed_documents(self, texts):
        return np.stack([self._vec(t) for t in texts]) if texts else np.zeros((0, self.dim), dtype=np.float32)

    def embed_query(self, text, instruction):
        return self._vec(text)


class OverlapReranker:
    """Log-odds from token overlap (Jaccard, centred and scaled), so tests exercise the real scoring path."""
    name = "fake-overlap-v2"

    def score(self, query, documents, instruction):
        q = set(tokens(query))
        out = []
        for d in documents:
            dt = set(tokens(d))
            j = len(q & dt) / max(1, len(q | dt))
            out.append(12.0 * (j - 0.12))
        return out
