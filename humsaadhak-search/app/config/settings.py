"""Settings from environment variables. No secret has a default.

Encryption in transit: TLS_CERT_FILE / TLS_KEY_FILE make the service speak HTTPS only (TLS 1.2+), and DATABASE_URL
should carry sslmode=verify-full&sslrootcert=... With REQUIRE_TLS=true (production) the service refuses to start
otherwise.
"""
import os
from dataclasses import dataclass
from urllib.parse import parse_qs, urlparse


@dataclass(frozen=True)
class Settings:
    service_token: str
    database_url: str | None
    embedding_model: str
    reranker_model: str
    embedding_dim: int
    retrieve_k: int
    rerank_k: int
    sector_bonus: float          # added to the reranker's log-odds when the sector is the same (app/util/scoring.py)
    calibration_path: str | None
    use_fake_models: bool
    tls_cert_file: str | None = None
    tls_key_file: str | None = None
    require_tls: bool = False
    rerank_temperature: float = 3.0   # spreads the reranker's verdicts into a readable 0-100 (app/util/scoring.py)
    refresh_index_on_start: bool = False  # re-embed records whose text or model changed (on by default in from_env)
    host: str = "0.0.0.0"
    port: int = 8000

    def transport_problems(self) -> list[str]:
        """What would travel unencrypted with these settings (empty when everything is encrypted and verified)."""
        problems = []
        if not (self.tls_cert_file and self.tls_key_file):
            problems.append("The service must serve HTTPS: set TLS_CERT_FILE and TLS_KEY_FILE.")
        if self.database_url:
            sslmode = (parse_qs(urlparse(self.database_url).query).get("sslmode") or [None])[0]
            if sslmode not in ("verify-full", "verify-ca"):
                problems.append("DATABASE_URL must use sslmode=verify-full with sslrootcert set; found "
                                + (f"sslmode={sslmode}" if sslmode else "no sslmode") + ".")
        return problems

    @staticmethod
    def from_env() -> "Settings":
        token = os.environ.get("SEARCH_TOKEN")
        if not token:
            raise RuntimeError("SEARCH_TOKEN must be set")
        settings = Settings(
            service_token=token,
            database_url=os.environ.get("DATABASE_URL"),
            embedding_model=os.environ.get("EMBEDDING_MODEL", "Qwen/Qwen3-Embedding-0.6B"),
            reranker_model=os.environ.get("RERANKER_MODEL", "Qwen/Qwen3-Reranker-0.6B"),
            embedding_dim=int(os.environ.get("EMBEDDING_DIM", "1024")),
            retrieve_k=int(os.environ.get("RETRIEVE_K", "100")),
            rerank_k=int(os.environ.get("RERANK_K", "50")),
            sector_bonus=float(os.environ.get("SECTOR_BONUS", "0.5")),
            calibration_path=os.environ.get("CALIBRATION_PATH"),
            use_fake_models=os.environ.get("USE_FAKE_MODELS", "false").lower() == "true",
            tls_cert_file=os.environ.get("TLS_CERT_FILE") or None,
            tls_key_file=os.environ.get("TLS_KEY_FILE") or None,
            require_tls=os.environ.get("REQUIRE_TLS", "false").lower() == "true",
            host=os.environ.get("HOST", "0.0.0.0"),
            port=int(os.environ.get("PORT", "8000")),
            rerank_temperature=float(os.environ.get("RERANK_TEMPERATURE", "3.0")),
            refresh_index_on_start=os.environ.get("REFRESH_INDEX_ON_START", "true").lower() == "true",
        )
        if settings.require_tls and settings.transport_problems():
            raise RuntimeError("Encryption in transit is required (REQUIRE_TLS=true), but: " + " ".join(settings.transport_problems()))
        return settings
