from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    assert client.get("/health").json() == {"status": "ok", "mode": "mock", "confidence": 1.0}


def test_classify_keyword_and_urgency():
    result = client.post("/classify", json={"title": "Unsafe water", "description": "Dirty village taps"})
    assert result.status_code == 200
    assert result.json() == {"category": "WATER_SANITATION", "urgency": "HIGH", "confidence": 0.94}


def test_embed_deterministic():
    body = {"text": "Village taps supply dirty water"}
    first = client.post("/embed", json=body).json()
    assert client.post("/embed", json=body).json() == first
    assert first["dimensions"] == len(first["embedding"]) == 3


def test_transcribe_stub():
    result = client.post("/transcribe", json={"audio_base64": "UklGRg==", "mime_type": "audio/wav", "language": "en"})
    assert result.status_code == 200
    assert result.json()["text"] == "Village taps supply dirty water"


def test_draft_proposal():
    result = client.post("/draft-proposal", json={"challenge": {"title": "Unsafe drinking water", "description": "Dirty water", "category": "WATER_SANITATION"}, "university": {"name": "Example University", "expertise": ["water treatment"]}})
    assert result.status_code == 200
    assert result.json()["confidence"] == 0.86
    assert "water treatment" in result.json()["proposal"]


def test_validation_errors_use_shared_contract():
    result = client.post("/classify", json={"title": "", "description": ""})
    assert result.status_code == 422
    assert result.json() == {"error": {"code": "INVALID_INPUT", "message": "Request validation failed"}, "confidence": 0.0}


def test_transcription_rejects_invalid_base64():
    result = client.post("/transcribe", json={"audio_base64": "not base64!", "mime_type": "audio/wav", "language": "en"})
    assert result.status_code == 422
    assert result.json()["error"]["code"] == "INVALID_INPUT"
