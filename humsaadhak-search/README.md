# humsaadhak-search

Semantic matching for HumSaadhak 2.0: donor purposes ↔ school causes and NGO projects, in both directions.

Pipeline per query: eligibility filter → dense retrieval (pgvector, HNSW) + keyword retrieval (PostgreSQL full text)
→ reciprocal rank fusion → cross-encoder rerank → sector boost → calibrated 0–100 score → explanation.
Results are always keyed by entity id.

## Code layout

Layered the same way as the Java backend, so both services read alike:

```
app/
├── main.py        builds the services and plugs in the controllers (uvicorn app.main:app)
├── controller/    FastAPI routers, HTTP only: health, index, match, feedback (+ the service-token check)
├── service/       SearchService (filter → retrieve → rerank → calibrate), MatchService, IndexService, FeedbackService
├── repository/    VectorStore interface; InMemoryStore (tests, demos) and PgVectorStore (pgvector, production)
├── dto/           request and response bodies (camelCase on the wire, like the Java client)
├── model/         Card, StoredDoc, Label, Hit
├── ml/            embedding and reranking models: Qwen3, plus deterministic stand-ins for tests
├── util/          reciprocal rank fusion, score calibration, explanations, tokenising
├── exception/     errors services raise; main.py turns them into HTTP responses
└── config/        settings from environment variables
```

`tests/test_architecture.py` fails the build if a controller reaches past the services, a service imports the web
framework, anything but the repository talks to PostgreSQL, or a lower layer imports a higher one.

## Run locally without models
```bash
pip install -e ".[dev]"
SEARCH_TOKEN=dev USE_FAKE_MODELS=true uvicorn app.main:app --reload
pytest
```

## Run with the real models (Qwen3-Embedding-0.6B + Qwen3-Reranker-0.6B, Apache 2.0)
```bash
pip install -e ".[models]"
SEARCH_TOKEN=... DATABASE_URL=postgresql://... uvicorn app.main:app
```
Upgrade path: set `EMBEDDING_MODEL=Qwen/Qwen3-Embedding-4B`, `RERANKER_MODEL=Qwen/Qwen3-Reranker-4B` on a GPU host, then `POST /v1/admin/reindex`.

## Learning loop
Every match decision in the Spring Boot app is posted to `/v1/feedback`.
* `python -m training.evaluate` — nDCG@10, Recall@50, precision above the show threshold.
* `python -m training.fit_calibration out/calibration.json` — needs ≥ 200 labels; then set `CALIBRATION_PATH`.
Promote a new model only when it beats the active one on held-out labels.

## Encryption in transit

The service speaks HTTPS only (TLS 1.2/1.3) when `TLS_CERT_FILE` and `TLS_KEY_FILE` are set, and reaches PostgreSQL with
`sslmode=verify-full&sslrootcert=...` in `DATABASE_URL`. With `REQUIRE_TLS=true` (production) it refuses to start
otherwise. Start it with `python -m app.config.server`. `tests/test_tls.py` makes real TLS connections to prove the
internal CA is trusted, other certificates and wrong hostnames are refused, and TLS 1.1 is refused.

## Environment
| Variable | Default | Meaning |
|---|---|---|
| `SEARCH_TOKEN` | required | Shared secret the Spring Boot app sends as `Authorization: Bearer …` |
| `DATABASE_URL` | none (in-memory) | PostgreSQL with pgvector; tables live in schema `search` |
| `EMBEDDING_MODEL` / `RERANKER_MODEL` | Qwen3 0.6B pair | Hugging Face model ids |
| `EMBEDDING_DIM` | 1024 | Vector size (Qwen3 supports shorter MRL sizes) |
| `RETRIEVE_K` / `RERANK_K` | 100 / 50 | Candidates per retriever / sent to the reranker |
| `SECTOR_BOOST` | 0.05 | Added to the rerank probability when sectors match |
| `CALIBRATION_PATH` | none | JSON from `fit_calibration` |
| `USE_FAKE_MODELS` | false | Deterministic stand-ins for tests and demos |
