"""Offline evaluation on held-out reviewer decisions.

Usage: python -m training.evaluate  (uses DATABASE_URL and the configured models)
Prints nDCG@10, Recall@50 and precision at the show threshold. Promote a model only if it beats the active one.
"""
from collections import defaultdict

from app.config.settings import Settings
from app.main import create_app
from training.metrics import ndcg_at_k, precision_above, recall_at_k


def evaluate(pipeline, labels, show_threshold: int = 50) -> dict:
    positives, judged = defaultdict(set), defaultdict(set)
    for l in labels:
        judged[l.purpose_id].add(l.need_id)
        if l.label == "positive":
            positives[l.purpose_id].add(l.need_id)
    n, r, pr = [], [], []
    for purpose_id, rel in positives.items():
        doc = pipeline.store.get("purpose", purpose_id)
        if not doc:
            continue
        hits = pipeline.search(doc.card, "need", "purpose->need", 50)
        ranked = [h.entity_id for h in hits]
        n.append(ndcg_at_k(ranked, rel, 10))
        r.append(recall_at_k(ranked, rel, 50))
        pr.append(precision_above([(h.entity_id, h.score) for h in hits if h.entity_id in judged[purpose_id]], rel, show_threshold))
    avg = lambda xs: round(sum(xs) / len(xs), 4) if xs else 0.0
    return {"purposes": len(n), "ndcg@10": avg(n), "recall@50": avg(r), f"precision@score>={show_threshold}": avg(pr),
            "model": pipeline.model_version}


if __name__ == "__main__":
    app = create_app(Settings.from_env())
    p = app.state.search
    print(evaluate(p, p.store.labels()))
