"""Starts the service: `python -m app.config.server`. HTTPS when TLS_CERT_FILE and TLS_KEY_FILE are set.

Python's SSL defaults already refuse anything older than TLS 1.2; tests/test_tls.py checks that with a real handshake.
"""
import uvicorn

from app.config.settings import Settings


def uvicorn_options(settings: Settings) -> dict:
    options = {"host": settings.host, "port": settings.port, "workers": 1}
    if settings.tls_cert_file and settings.tls_key_file:
        options.update(ssl_certfile=settings.tls_cert_file, ssl_keyfile=settings.tls_key_file)
    return options


def main() -> None:
    settings = Settings.from_env()          # refuses to start without TLS when REQUIRE_TLS=true
    uvicorn.run("app.main:app", **uvicorn_options(settings))


if __name__ == "__main__":
    main()
