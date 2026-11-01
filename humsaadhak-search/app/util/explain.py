"""Short, honest reasons for a match: the chips shown next to every score in the UI."""
import re

import numpy as np

from app.model.card import Card
from app.ml.base import Embedder
from app.util.text import tokens

SENTENCE = re.compile(r"(?<=[.!?])\s+")
# Words nearly every purpose and need uses: shared, they say nothing about why this pair matches
GENERIC = {"need", "needs", "needed", "require", "required", "requirement", "fund", "funds", "funding", "funded", "provide",
           "provides", "providing", "provided", "support", "supporting", "help", "helping", "donate", "donation", "donations",
           "give", "giving", "money", "want", "interested", "would", "like", "will", "can", "we", "my", "us", "you", "your",
           "all", "also", "more", "new", "some", "so", "they", "them", "who", "which", "not", "has", "have", "per", "each",
           "it", "am", "was", "were", "do", "does", "get", "make", "use", "used", "through", "into", "about", "under"}


def _stem(t: str) -> str:
    """Singular and plural count as the same word ("book" and "books")."""
    return t[:-1] if len(t) > 3 and t.endswith("s") and not t.endswith("ss") else t


def explain(query: Card, doc: Card, embedder: Embedder, query_vec: np.ndarray) -> dict:
    doc_words = {_stem(t) for t in tokens(doc.name + " " + doc.description)}
    shared = [t for t in dict.fromkeys(tokens(query.name + " " + query.description)) if t not in GENERIC and _stem(t) in doc_words]
    sentences = [s.strip() for s in SENTENCE.split(doc.description) if len(s.strip()) > 20] or [doc.description.strip()]
    best = sentences[0]
    if len(sentences) > 1:
        vecs = embedder.embed_documents(sentences)
        best = sentences[int(np.argmax(vecs @ query_vec))]
    return {
        "sameSector": query.sector == doc.sector,
        "sharedTerms": ", ".join(shared[:5]),
        "bestSentence": best[:240],
    }
