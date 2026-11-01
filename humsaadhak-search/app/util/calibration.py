"""Maps a raw reranker probability to a calibrated 0-100 score.

The mapping is piecewise linear through points fitted by isotonic regression on reviewer decisions
(training/fit_calibration.py), so "70" means roughly 70% of similar pairs were approved by staff.
Until enough labels exist, the identity mapping is used.
"""
import json

import numpy as np


class Calibrator:
    def __init__(self, xs: list[float] | None = None, ys: list[float] | None = None, version: str = "identity"):
        self.xs = np.asarray(xs if xs else [0.0, 1.0], dtype=float)
        self.ys = np.asarray(ys if ys else [0.0, 1.0], dtype=float)
        if len(self.xs) != len(self.ys) or np.any(np.diff(self.xs) < 0) or np.any(np.diff(self.ys) < 0):
            raise ValueError("calibration points must be the same length and non-decreasing")
        self.version = version

    def __call__(self, raw: float) -> int:
        return int(round(100 * float(np.interp(min(max(raw, 0.0), 1.0), self.xs, self.ys))))

    @staticmethod
    def load(path: str | None) -> "Calibrator":
        if not path:
            return Calibrator()
        with open(path, encoding="utf-8") as f:
            d = json.load(f)
        return Calibrator(d["xs"], d["ys"], d.get("version", path))
