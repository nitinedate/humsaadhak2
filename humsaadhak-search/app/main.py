"""FastAPI entry point (`uvicorn app.main:app`): builds the services and plugs in the controllers.

Layers, as in the Java backend:
    controller -> service -> repository (vector store)      dto, model, util shared; ml = embedding and reranking models
"""
import logging
import os
import threading

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.config.settings import Settings
from app.controller import feedback_controller, health_controller, index_controller, match_controller
from app.exception.errors import ServiceError
from app.ml.base import HashingEmbedder, OverlapReranker
from app.repository.memory_store import InMemoryStore
from app.service.feedback_service import FeedbackService
from app.service.index_service import IndexService
from app.service.match_service import MatchService
from app.service.search_service import SearchService
from app.util.calibration import Calibrator


def build_models(settings: Settings):
    """The embedding and reranking models (or their stand-ins with USE_FAKE_MODELS=true)."""
    if settings.use_fake_models:
        return HashingEmbedder(settings.embedding_dim), OverlapReranker()
    from app.ml.qwen import Qwen3Embedder, Qwen3Reranker
    return Qwen3Embedder(settings.embedding_model, settings.embedding_dim), Qwen3Reranker(settings.reranker_model)


def build_search_service(settings: Settings) -> SearchService:
    embedder, reranker = build_models(settings)
    if settings.database_url:
        from app.repository.pg_store import PgVectorStore
        store = PgVectorStore(settings.database_url, settings.embedding_dim)
    else:
        store = InMemoryStore()
    return SearchService(store, embedder, reranker, Calibrator.load(settings.calibration_path),
                         settings.retrieve_k, settings.rerank_k, settings.sector_bonus, settings.rerank_temperature)


def create_app(settings: Settings, search: SearchService | None = None) -> FastAPI:
    app = FastAPI(title="HumSaadhak search", version="2.0.0")
    search = search or build_search_service(settings)
    app.state.settings = settings
    app.state.search = search
    app.state.match = MatchService(search)
    app.state.indexing = IndexService(search)
    app.state.feedback = FeedbackService(search)
    if settings.refresh_index_on_start:
        # In the background, so the service answers at once: records whose text or model changed are re-embedded
        def refresh():
            try:
                n = app.state.indexing.refresh_stale()
                logging.getLogger("humsaadhak.search").info("Index up to date: %d record(s) re-embedded", n)
            except Exception:  # noqa: BLE001 - a failed refresh must not stop the service; the next start retries
                logging.getLogger("humsaadhak.search").exception("Index refresh failed; it is retried at the next start")
        threading.Thread(target=refresh, name="index-refresh", daemon=True).start()

    @app.exception_handler(ServiceError)
    def service_error(_: Request, e: ServiceError):
        return JSONResponse(status_code=e.status, content={"detail": str(e)})

    for controller in (health_controller, index_controller, match_controller, feedback_controller):
        app.include_router(controller.router)
    return app


app = None
try:  # created lazily so importing the module in tests does not require SEARCH_TOKEN
    if os.environ.get("SEARCH_TOKEN"):
        app = create_app(Settings.from_env())
except RuntimeError:
    app = None
