"""From the reranker's verdict to a 0-1 relevance, which the calibrator turns into the match percentage.

The reranker answers "does this cause or project meet the donor's purpose?" with log-odds z = logit(yes) - logit(no).
Its probability sigmoid(z) is close to 1 for any plausible match (say 0.97 for building a library and 0.99 for books,
when the purpose is books for poor students), so read directly it cannot tell a good match from the best one, and
every candidate showed 100. Dividing z by a temperature keeps those differences: with T = 3,
    z = 9 -> 95%    z = 6 -> 88%    z = 3 -> 73%    z = 0 -> 50% (the model is undecided)    z = -3 -> 27%
The same-sector bonus is added to z as well, so it moves a score a little and can never push it past 100.
Reviewer decisions later refine the mapping from relevance to percentage (training/fit_calibration.py).
"""
import math


def sigmoid(x: float) -> float:
    if x >= 0:
        return 1.0 / (1.0 + math.exp(-x))
    e = math.exp(x)
    return e / (1.0 + e)


def relevance(log_odds: float, same_sector: bool, sector_bonus: float, temperature: float) -> float:
    """Probability-like relevance in (0, 1): above 0.5 when the reranker leans towards "this is a match"."""
    if temperature <= 0:
        raise ValueError("temperature must be positive")
    return sigmoid((log_odds + (sector_bonus if same_sector else 0.0)) / temperature)
