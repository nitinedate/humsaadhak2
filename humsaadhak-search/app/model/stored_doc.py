"""An indexed card with its vector and the hash of the text it was embedded from."""
from dataclasses import dataclass

import numpy as np

from app.model.card import Card


@dataclass
class StoredDoc:
    card: Card
    vector: np.ndarray
    text_hash: str
