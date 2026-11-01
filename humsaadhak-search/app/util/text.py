"""Tokenising shared by keyword search and explanations (English and Devanagari)."""
import re

TOKEN = re.compile(r"[a-z0-9\u0900-\u097f]+")
STOP = {"a", "an", "and", "the", "of", "for", "to", "in", "on", "with", "by", "at", "from", "is", "are", "be",
        "this", "that", "their", "our", "its", "as", "or", "rs", "school", "cause", "ngo", "project", "donor", "purpose",
        "sector", "amount", "periodicity", "time", "one", "yearly", "monthly", "quarterly"}


def tokens(text: str) -> list[str]:
    return [t for t in TOKEN.findall(text.lower()) if t not in STOP and len(t) > 1]
