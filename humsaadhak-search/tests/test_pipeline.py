from tests.conftest import AUTH


def test_relevant_needs_rank_first_and_results_are_keyed_by_id(pipeline):
    query = pipeline.store.get("purpose", 100).card
    hits = pipeline.search(query, "need", "purpose->need", top_k=5)
    ids = [h.entity_id for h in hits]
    assert set(ids[:2]) == {1, 2}                      # the two classroom / computer-lab needs
    assert [h.rank for h in hits] == list(range(1, len(hits) + 1))
    assert all(0 <= h.score <= 100 for h in hits)
    assert hits[0].explanation["sameSector"] is True
    assert hits[0].explanation["sharedTerms"]


def test_reindex_skips_unchanged_text(pipeline):
    card = pipeline.store.get("need", 1).card
    assert pipeline.index(card) is False
    assert pipeline.index(card.model_copy(update={"description": card.description + " Updated."})) is True


def test_api_requires_service_token(client):
    assert client.post("/v1/match/purpose-to-needs", json={"queryId": 100, "queryType": "purpose"}).status_code == 401
    assert client.get("/v1/health").status_code == 200


def test_api_contract_matches_java_client(client):
    r = client.post("/v1/match/purpose-to-needs", headers=AUTH, json={"queryId": 100, "queryType": "purpose", "topK": 3, "filters": {}})
    assert r.status_code == 200
    body = r.json()
    assert set(body) == {"modelVersion", "hits"}
    assert set(body["hits"][0]) == {"entityId", "rank", "retrievalScore", "rerankScore", "score", "explanation"}


def test_upsert_delete_and_reverse_search(client):
    card = {"entityType": "need", "entityId": 9, "kind": "CAUSE", "name": "Computer lab for Ashram Shala",
            "description": "Ten computers and a projector for a rural residential school computer lab.", "sector": "Education"}
    assert client.post("/v1/index/upsert", headers=AUTH, json=card).json() == {"reindexed": True}
    r = client.post("/v1/match/need-to-purposes", headers=AUTH, json={"queryId": 9, "queryType": "need"})
    assert r.json()["hits"][0]["entityId"] == 100
    assert client.delete("/v1/index/need/9", headers=AUTH).status_code == 204
    assert client.post("/v1/match/need-to-purposes", headers=AUTH, json={"queryId": 9, "queryType": "need"}).status_code == 404


def test_feedback_is_stored_as_labels(client, pipeline):
    r = client.post("/v1/feedback", headers=AUTH, json=[{"purposeId": 100, "needId": 1, "label": "positive", "source": "review"},
                                                         {"purposeId": 100, "needId": 5, "label": "negative", "source": "review"}])
    assert r.json() == {"stored": 2}
    assert [l.label for l in pipeline.store.labels()] == ["positive", "negative"]


def test_free_text_search(client):
    r = client.post("/v1/search", headers=AUTH, json={"q": "millet nutrition for anganwadi children", "topK": 3})
    assert r.json()["hits"][0]["entityId"] == 4


def test_evaluation_runs_on_labels(pipeline):
    from app.model.label import Label
    from training.evaluate import evaluate
    pipeline.store.add_labels([Label(100, 1, "positive", "review"), Label(100, 2, "positive", "review"),
                               Label(100, 5, "negative", "review")])
    result = evaluate(pipeline, pipeline.store.labels())
    assert result["purposes"] == 1 and result["ndcg@10"] > 0.9 and result["recall@50"] == 1.0
