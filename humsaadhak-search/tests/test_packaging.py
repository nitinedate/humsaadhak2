"""The Docker build installs this project with pip and bakes in the models: guard what that build depends on."""
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def test_packages_are_listed_explicitly():
    # Two top-level packages in a flat layout: without this list setuptools refuses to build ("Multiple top-level packages")
    project = tomllib.loads((ROOT / "pyproject.toml").read_text())
    assert project["build-system"]["build-backend"] == "setuptools.build_meta"
    include = project["tool"]["setuptools"]["packages"]["find"]["include"]
    assert "app*" in include and "training*" in include
    assert not any(p.startswith("tests") for p in include)


def test_image_bakes_cpu_models_readable_by_the_service_and_runs_offline():
    docker = (ROOT / "Dockerfile").read_text()
    assert "--index-url https://download.pytorch.org/whl/cpu" in docker      # no 3 GB of CUDA libraries
    assert docker.index("whl/cpu") < docker.index("pip install -r /tmp/requirements.txt")   # CPU torch first, so it is kept
    assert "['optional-dependencies']['models']" in docker                   # the libraries come from pyproject.toml
    assert "ENV HF_HOME=/opt/hf-cache" in docker and "chmod -R a+rX /opt/hf-cache" in docker
    assert docker.index("HF_HUB_OFFLINE=1") > docker.index("from_pretrained")  # offline only after the download
    assert docker.index("USER search") > docker.index("HF_HUB_OFFLINE=1")


def test_a_code_change_does_not_download_libraries_or_models_again():
    # Docker reuses a layer only while everything before it is unchanged: the code is copied after the downloads
    docker = (ROOT / "Dockerfile").read_text()
    downloads_end = docker.index("from_pretrained")
    assert docker.index("COPY pyproject.toml ./") < docker.index("whl/cpu")
    assert docker.index("COPY app ./app") > downloads_end and docker.index("COPY training ./training") > downloads_end
    assert "COPY . " not in docker
    assert 'CMD ["python", "-m", "app.config.server"]' in docker              # runs from /srv: nothing to install


def test_build_context_leaves_out_caches_and_tests():
    ignored = (ROOT / ".dockerignore").read_text().split()
    for entry in ("**/__pycache__", ".pytest_cache", "tests", ".venv"):
        assert entry in ignored


def test_downloads_survive_a_stall_and_a_failed_build():
    docker = (ROOT / "Dockerfile").read_text()
    assert "PIP_DEFAULT_TIMEOUT=60" in docker and "PIP_RETRIES=10" in docker       # a stalled download is dropped and retried
    assert docker.count("for attempt in 1 2 3 4 5; do") == 3                     # torch, libraries and models each retry
    assert docker.count("--mount=type=cache,target=/root/.cache/pip") == 2        # finished downloads are kept between tries
    assert "--mount=type=cache,target=/root/.cache/hf-download" in docker          # so are the 2.4 GB of models
    assert "HF_HUB_DOWNLOAD_TIMEOUT=120" in docker
    assert "cp -a /root/.cache/hf-download/. /opt/hf-cache/" in docker             # the models end up in the image
    assert "--no-cache-dir" not in docker
