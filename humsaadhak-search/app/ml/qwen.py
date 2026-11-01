"""Qwen3 embedding and reranking models (Apache 2.0). Imported only when real models are enabled."""
from typing import Sequence

import numpy as np


class Qwen3Embedder:
    def __init__(self, model_name: str, dim: int = 1024, device: str | None = None):
        from sentence_transformers import SentenceTransformer  # heavy import kept local
        self.name = model_name
        self.dim = dim
        self.model = SentenceTransformer(model_name, device=device, truncate_dim=dim,
                                         tokenizer_kwargs={"padding_side": "left"})

    def embed_documents(self, texts: Sequence[str]) -> np.ndarray:
        return np.asarray(self.model.encode(list(texts), normalize_embeddings=True, batch_size=16), dtype=np.float32)

    def embed_query(self, text: str, instruction: str) -> np.ndarray:
        prompt = f"Instruct: {instruction}\nQuery:"
        return np.asarray(self.model.encode([text], prompt=prompt, normalize_embeddings=True)[0], dtype=np.float32)


class Qwen3Reranker:
    """Cross-encoder scoring as in the Qwen3-Reranker model card, for 'does the document meet the query'. Returns the
    log-odds logit(yes) - logit(no) rather than P(yes): the probability saturates near 1 for every plausible match,
    the log-odds keep the difference between a good match and the best one (see app/util/scoring.py)."""

    PREFIX = ("<|im_start|>system\nJudge whether the Document meets the requirements based on the Query and the "
              "Instruct provided. Note that the answer can only be \"yes\" or \"no\".<|im_end|>\n<|im_start|>user\n")
    SUFFIX = "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"

    def __init__(self, model_name: str, device: str | None = None, max_length: int = 2048):
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer
        self.torch = torch
        self.name = model_name
        self.tok = AutoTokenizer.from_pretrained(model_name, padding_side="left")
        self.model = AutoModelForCausalLM.from_pretrained(model_name).eval()
        if device:
            self.model.to(device)
        self.yes = self.tok.convert_tokens_to_ids("yes")
        self.no = self.tok.convert_tokens_to_ids("no")
        self.max_length = max_length

    def score(self, query: str, documents: Sequence[str], instruction: str) -> list[float]:
        texts = [f"{self.PREFIX}<Instruct>: {instruction}\n<Query>: {query}\n<Document>: {d}{self.SUFFIX}" for d in documents]
        out: list[float] = []
        with self.torch.no_grad():
            for i in range(0, len(texts), 8):
                batch = self.tok(texts[i:i + 8], padding=True, truncation=True, max_length=self.max_length,
                                 return_tensors="pt").to(self.model.device)
                logits = self.model(**batch).logits[:, -1, :].float()
                out.extend((logits[:, self.yes] - logits[:, self.no]).tolist())
        return out
