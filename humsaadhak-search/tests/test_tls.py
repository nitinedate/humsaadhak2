"""Encryption in transit for the search service: the production check, and real TLS handshakes against the service
served over HTTPS with the test certificates in tests/tls (made by deploy/tls/make-certs.sh)."""
import pathlib
import socket
import ssl
import threading
import time

import httpx
import pytest
import uvicorn

from app.config.server import uvicorn_options
from app.config.settings import Settings
from app.main import create_app
from tests.conftest import settings

TLS = pathlib.Path(__file__).parent / "tls"
CA = str(TLS / "ca.crt")


def test_production_refuses_unencrypted_settings(monkeypatch):
    s = settings()
    assert s.transport_problems() == ["The service must serve HTTPS: set TLS_CERT_FILE and TLS_KEY_FILE."]
    plain_db = Settings(**{**s.__dict__, "tls_cert_file": "c", "tls_key_file": "k", "database_url": "postgresql://u:p@db/h?sslmode=require"})
    assert plain_db.transport_problems() == ["DATABASE_URL must use sslmode=verify-full with sslrootcert set; found sslmode=require."]
    good = Settings(**{**plain_db.__dict__, "database_url": "postgresql://u:p@db/h?sslmode=verify-full&sslrootcert=/tls/ca.crt"})
    assert good.transport_problems() == []

    monkeypatch.setenv("SEARCH_TOKEN", "t")
    monkeypatch.setenv("REQUIRE_TLS", "true")
    monkeypatch.setenv("DATABASE_URL", "postgresql://u:p@db/h")
    with pytest.raises(RuntimeError, match="Encryption in transit is required"):
        Settings.from_env()
    monkeypatch.setenv("TLS_CERT_FILE", "/tls/tls.crt")
    monkeypatch.setenv("TLS_KEY_FILE", "/tls/tls.key")
    monkeypatch.setenv("DATABASE_URL", "postgresql://u:p@db/h?sslmode=verify-full&sslrootcert=/tls/ca.crt")
    assert Settings.from_env().require_tls is True
    assert uvicorn_options(Settings.from_env())["ssl_certfile"] == "/tls/tls.crt"


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@pytest.fixture
def https_server(pipeline):
    """The real service, served over HTTPS on a free port; yields (port, start_with_cert) so tests can swap certificates."""
    servers = []

    def start(cert="server.crt", key="server.key") -> int:
        port = _free_port()
        config = uvicorn.Config(create_app(settings(), pipeline), host="127.0.0.1", port=port, log_level="error",
                                ssl_certfile=str(TLS / cert), ssl_keyfile=str(TLS / key))
        server = uvicorn.Server(config)
        threading.Thread(target=server.run, daemon=True).start()
        for _ in range(100):
            if server.started:
                break
            time.sleep(0.05)
        servers.append(server)
        return port

    yield start
    for s in servers:
        s.should_exit = True


def test_clients_trusting_the_internal_ca_connect_others_are_refused(https_server):
    port = https_server()
    assert httpx.get(f"https://localhost:{port}/v1/health", verify=ssl.create_default_context(cafile=CA)).json() == {"status": "ok"}
    with pytest.raises(httpx.ConnectError):            # public CAs only: the internal CA is unknown
        httpx.get(f"https://localhost:{port}/v1/health")
    with pytest.raises(httpx.ConnectError):            # the certificate does not name 127.0.0.1
        httpx.get(f"https://127.0.0.1:{port}/v1/health", verify=ssl.create_default_context(cafile=CA))
    with pytest.raises(httpx.HTTPError):               # plain HTTP is not served at all
        httpx.get(f"http://localhost:{port}/v1/health", timeout=3)


def test_a_certificate_from_another_ca_is_refused(https_server):
    port = https_server("rogue-server.crt", "rogue-server.key")
    with pytest.raises(httpx.ConnectError):
        httpx.get(f"https://localhost:{port}/v1/health", verify=ssl.create_default_context(cafile=CA))


def _handshake(port: int, maximum: ssl.TLSVersion) -> str:
    ctx = ssl.create_default_context(cafile=CA)
    ctx.minimum_version = ssl.TLSVersion.MINIMUM_SUPPORTED
    ctx.maximum_version = maximum
    with socket.create_connection(("localhost", port), timeout=5) as raw, ctx.wrap_socket(raw, server_hostname="localhost") as tls:
        return tls.version()


def test_only_tls_1_2_and_1_3_are_spoken(https_server):
    port = https_server()
    assert _handshake(port, ssl.TLSVersion.MAXIMUM_SUPPORTED) == "TLSv1.3"
    assert _handshake(port, ssl.TLSVersion.TLSv1_2) == "TLSv1.2"
    with pytest.raises((ssl.SSLError, OSError)):
        _handshake(port, ssl.TLSVersion.TLSv1_1)
