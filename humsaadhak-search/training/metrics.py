"""Ranking metrics used to decide whether a new model may replace the active one."""
import math


def ndcg_at_k(ranked: list[int], relevant: set[int], k: int = 10) -> float:
    dcg = sum(1.0 / math.log2(i + 2) for i, d in enumerate(ranked[:k]) if d in relevant)
    ideal = sum(1.0 / math.log2(i + 2) for i in range(min(k, len(relevant))))
    return dcg / ideal if ideal else 0.0


def recall_at_k(ranked: list[int], relevant: set[int], k: int = 50) -> float:
    return len(set(ranked[:k]) & relevant) / len(relevant) if relevant else 0.0


def precision_above(scored: list[tuple[int, int]], relevant: set[int], threshold: int) -> float:
    shown = [d for d, s in scored if s >= threshold]
    return sum(1 for d in shown if d in relevant) / len(shown) if shown else 0.0
