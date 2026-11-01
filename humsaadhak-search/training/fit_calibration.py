"""Fits the score calibration from reviewer decisions (requires the `training` extra: scikit-learn).

Usage: python -m training.fit_calibration out/calibration.json
Then set CALIBRATION_PATH=out/calibration.json and restart the service.
"""
import json
import sys

import numpy as np

from app.config.settings import Settings
from app.main import create_app


def fit(raw_scores: list[float], outcomes: list[int], version: str) -> dict:
    from sklearn.isotonic import IsotonicRegression
    iso = IsotonicRegression(y_min=0.0, y_max=1.0, out_of_bounds="clip").fit(raw_scores, outcomes)
    xs = np.linspace(0, 1, 21)
    ys = np.maximum.accumulate(iso.predict(xs))
    return {"version": version, "xs": xs.round(4).tolist(), "ys": ys.round(4).tolist()}


if __name__ == "__main__":
    out = sys.argv[1]
    app = create_app(Settings.from_env())
    p = app.state.search
    raw, y = [], []
    for l in p.store.labels():
        purpose, need = p.store.get("purpose", l.purpose_id), p.store.get("need", l.need_id)
        if purpose and need:
            raw.append(p.relevance(purpose.card, need.card, "purpose->need"))   # exactly what the live search calibrates
            y.append(1 if l.label == "positive" else 0)
    if len(y) < 200:
        sys.exit(f"Only {len(y)} labelled pairs; collect at least 200 before calibrating.")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(fit(raw, y, f"isotonic-{len(y)}"), f, indent=2)
    print(f"wrote {out} from {len(y)} labels")
