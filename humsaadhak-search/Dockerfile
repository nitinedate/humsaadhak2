FROM python:3.12-slim
WORKDIR /srv
RUN useradd --uid 10002 --create-home search   # fixed id: owns its TLS key (deploy/compose.yaml)
# HF_HOME: the model weights live here, readable by the service user (not in root's home, which it cannot read).
# A download that stalls for 60 seconds is dropped and tried again (pip's own default gives up after 15 seconds).
ENV HF_HOME=/opt/hf-cache PIP_DISABLE_PIP_VERSION_CHECK=1 PIP_DEFAULT_TIMEOUT=60 PIP_RETRIES=10

# 1. Libraries and models first, from pyproject.toml alone, so a change to the service's code reuses these layers and
#    downloads nothing. pip's downloads stay in a build cache (not part of the image), and each step tries up to 5 times:
#    a download that stalls part-way (pip does not retry those itself) costs seconds, not the whole build.
COPY pyproject.toml ./
# PyTorch's CPU build: the server has no GPU, and the default CUDA build adds about 3 GB of NVIDIA libraries.
RUN --mount=type=cache,target=/root/.cache/pip \
    for attempt in 1 2 3 4 5; do \
      pip install torch --index-url https://download.pytorch.org/whl/cpu && exit 0; \
      echo "PyTorch download failed (attempt $attempt of 5); trying again in 15 seconds"; sleep 15; \
    done; exit 1
# The service's libraries and the models' (the [models] extra), read from pyproject.toml; torch is already there
RUN --mount=type=cache,target=/root/.cache/pip \
    python -c "import tomllib; p = tomllib.load(open('pyproject.toml', 'rb'))['project']; print('\n'.join(p['dependencies'] + p['optional-dependencies']['models']))" > /tmp/requirements.txt \
 && for attempt in 1 2 3 4 5; do \
      pip install -r /tmp/requirements.txt && exit 0; \
      echo "Library download failed (attempt $attempt of 5); trying again in 15 seconds"; sleep 15; \
    done; exit 1
# Bake the model weights (about 2.4 GB) into the image so start-up does not depend on the internet. They are downloaded
# into a build cache first, so a retry resumes where it stopped, then copied into the image.
RUN --mount=type=cache,target=/root/.cache/hf-download \
    for attempt in 1 2 3 4 5; do \
      HF_HOME=/root/.cache/hf-download HF_HUB_DOWNLOAD_TIMEOUT=120 HF_HUB_ETAG_TIMEOUT=60 \
      python -c "from sentence_transformers import SentenceTransformer as S; S('Qwen/Qwen3-Embedding-0.6B'); \
from transformers import AutoTokenizer, AutoModelForCausalLM as M; AutoTokenizer.from_pretrained('Qwen/Qwen3-Reranker-0.6B'); M.from_pretrained('Qwen/Qwen3-Reranker-0.6B')" \
      && mkdir -p /opt/hf-cache && cp -a /root/.cache/hf-download/. /opt/hf-cache/ && chmod -R a+rX /opt/hf-cache && exit 0; \
      echo "Model download failed (attempt $attempt of 5); trying again in 15 seconds"; sleep 15; \
    done; exit 1
# At run time the models come only from the image: never from the internet
ENV HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1

# 2. The service's code last: changing it rebuilds only these quick layers. It runs from /srv (python -m app...).
COPY app ./app
COPY training ./training
# /models: fine-tuned models (training/fine_tune.py), kept in a volume (deploy/compose.yaml) and owned by the service user
RUN mkdir -p /models && chown search /models
USER search
EXPOSE 8000
CMD ["python", "-m", "app.config.server"]
