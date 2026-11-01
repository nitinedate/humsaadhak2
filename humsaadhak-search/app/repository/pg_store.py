"""PostgreSQL + pgvector store. Owns schema `search`; creates its tables on start-up."""
import json

import numpy as np
from pgvector.psycopg import register_vector
from psycopg_pool import ConnectionPool

from app.model.card import Card
from app.model.label import Label
from app.model.stored_doc import StoredDoc
from app.util.text import tokens

DDL = """
create extension if not exists vector;
create schema if not exists search;
create table if not exists search.document (
  entity_type text not null,
  entity_id bigint not null,
  kind text not null,
  sector text not null,
  card jsonb not null,
  body text not null,
  text_hash char(64) not null,
  embedding vector({dim}) not null,
  tsv tsvector generated always as (to_tsvector('simple', body)) stored,
  updated_at timestamptz not null default now(),
  primary key (entity_type, entity_id)
);
create index if not exists ix_search_doc_hnsw on search.document using hnsw (embedding vector_cosine_ops);
create index if not exists ix_search_doc_tsv on search.document using gin (tsv);
create table if not exists search.relevance_label (
  id bigserial primary key,
  purpose_id bigint not null,
  need_id bigint not null,
  label text not null check (label in ('positive','negative')),
  source text not null,
  created_at timestamptz not null default now()
);
"""


class PgVectorStore:
    def __init__(self, url: str, dim: int):
        self.pool = ConnectionPool(url, min_size=1, max_size=8, configure=register_vector, open=True)
        with self.pool.connection() as c:
            c.execute(DDL.format(dim=dim))

    def upsert(self, card: Card, vector, text_hash):
        with self.pool.connection() as c:
            c.execute("""insert into search.document(entity_type, entity_id, kind, sector, card, body, text_hash, embedding)
                         values (%s,%s,%s,%s,%s,%s,%s,%s)
                         on conflict (entity_type, entity_id) do update set kind=excluded.kind, sector=excluded.sector,
                           card=excluded.card, body=excluded.body, text_hash=excluded.text_hash, embedding=excluded.embedding,
                           updated_at=now()""",
                      (card.entity_type, card.entity_id, card.kind, card.sector, json.dumps(card.model_dump()),
                       card.text(), text_hash, np.asarray(vector, dtype=np.float32)))

    def delete(self, entity_type, entity_id):
        with self.pool.connection() as c:
            c.execute("delete from search.document where entity_type=%s and entity_id=%s", (entity_type, entity_id))

    def get(self, entity_type, entity_id):
        with self.pool.connection() as c:
            row = c.execute("select card, embedding, text_hash from search.document where entity_type=%s and entity_id=%s",
                            (entity_type, entity_id)).fetchone()
        return StoredDoc(Card(**row[0]), np.asarray(row[1]), row[2]) if row else None

    def dense_search(self, entity_type, query, k, sector=None):
        sql = "select entity_id, 1 - (embedding <=> %s) from search.document where entity_type=%s"
        args: list = [np.asarray(query, dtype=np.float32), entity_type]
        if sector:
            sql += " and sector=%s"
            args.append(sector)
        sql += " order by embedding <=> %s limit %s"
        args += [np.asarray(query, dtype=np.float32), k]
        with self.pool.connection() as c:
            return [(r[0], float(r[1])) for r in c.execute(sql, args).fetchall()]

    def keyword_search(self, entity_type, query_text, k, sector=None):
        # OR the query's content words: a long purpose text must not require every word to appear.
        terms = list(dict.fromkeys(tokens(query_text)))[:40]
        if not terms:
            return []
        sql = """select entity_id, ts_rank_cd(tsv, q) from search.document, to_tsquery('simple', %s) q
                 where entity_type=%s and tsv @@ q"""
        args: list = [" | ".join(terms), entity_type]
        if sector:
            sql += " and sector=%s"
            args.append(sector)
        sql += " order by 2 desc limit %s"
        args.append(k)
        with self.pool.connection() as c:
            return [(r[0], float(r[1])) for r in c.execute(sql, args).fetchall()]

    def all_ids(self, entity_type):
        with self.pool.connection() as c:
            return [r[0] for r in c.execute("select entity_id from search.document where entity_type=%s", (entity_type,))]

    def add_labels(self, labels: list[Label]):
        with self.pool.connection() as c:
            with c.cursor() as cur:
                cur.executemany("insert into search.relevance_label(purpose_id, need_id, label, source) values (%s,%s,%s,%s)",
                                [(l.purpose_id, l.need_id, l.label, l.source) for l in labels])

    def labels(self):
        with self.pool.connection() as c:
            return [Label(r[0], r[1], r[2], r[3]) for r in
                    c.execute("select purpose_id, need_id, label, source from search.relevance_label order by id")]
