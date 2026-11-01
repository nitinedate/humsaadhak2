"""Teaches the embedding model HumSaadhak's own matches, from reviewers' decisions (needs the models extra, as in the image).

Usage (in the search container): python -m training.fine_tune /models/humsaadhak-embedding
    deploy_local.cmd match-train   (or deploy/deploy.sh match-train) runs it for you.
Every approved purpose-need pair is an example of a match; the other pairs in a batch serve as examples of non-matches.
It needs at least MIN_APPROVED approved pairs. Afterwards: set EMBEDDING_MODEL=/models/humsaadhak-embedding in the
settings file and restart (the index re-embeds itself), then compare `match-eval` and `python -m training.evaluate`
with the previous model before keeping it.
"""
import sys

from app.model.card import INSTRUCTIONS

MIN_APPROVED = 100


def training_examples(store) -> list[tuple[str, str]]:
    """(purpose as a search query, with the instruction the service uses; approved need) for every approved pair."""
    prompt = f"Instruct: {INSTRUCTIONS['purpose->need']}\nQuery:"
    out = []
    for label in store.labels():
        if label.label != "positive":
            continue
        purpose, need = store.get("purpose", label.purpose_id), store.get("need", label.need_id)
        if purpose and need:
            out.append((prompt + purpose.card.text(), need.card.text()))
    return list(dict.fromkeys(out))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: python -m training.fine_tune <output folder>, for example /models/humsaadhak-embedding")
    from app.config.settings import Settings
    from app.repository.pg_store import PgVectorStore
    settings = Settings.from_env()
    if not settings.database_url:
        sys.exit("DATABASE_URL is not set: the reviewers' decisions are read from the database.")
    examples = training_examples(PgVectorStore(settings.database_url, settings.embedding_dim))
    if len(examples) < MIN_APPROVED:
        sys.exit(f"Only {len(examples)} approved matches so far; fine-tuning needs at least {MIN_APPROVED}. "
                 "Keep reviewing in Match Studio and run this again later.")
    from sentence_transformers import InputExample, SentenceTransformer, losses
    from torch.utils.data import DataLoader
    model = SentenceTransformer(settings.embedding_model, tokenizer_kwargs={"padding_side": "left"})
    loader = DataLoader([InputExample(texts=[q, d]) for q, d in examples], shuffle=True, batch_size=16)
    model.fit(train_objectives=[(loader, losses.MultipleNegativesRankingLoss(model))], epochs=1,
              warmup_steps=max(1, len(examples) // 160), show_progress_bar=True)
    model.save(sys.argv[1])
    print(f"Saved to {sys.argv[1]} from {len(examples)} approved matches. Set EMBEDDING_MODEL={sys.argv[1]} and restart.")
