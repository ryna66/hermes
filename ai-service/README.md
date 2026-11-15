# SIH AI service

Python 3.11 stateless FastAPI service. Mock mode is deterministic and requires no credentials.

```sh
cd ai-service
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Set `AI_MODE=real` to select the real-provider adapter interfaces. Configure `CLASSIFICATION_PROVIDER`, `EMBEDDING_PROVIDER`, `TRANSCRIPTION_PROVIDER`, and `DRAFTING_PROVIDER` to identify providers; adapters return a clear 503 until a provider implementation is configured. `EMBEDDING_DIMENSIONS` sets the mock vector size (default 3).

```sh
curl http://localhost:8001/health
curl -X POST http://localhost:8001/classify -H 'Content-Type: application/json' -d '{"title":"Unsafe drinking water","description":"Village taps supply dirty water"}'
curl -X POST http://localhost:8001/embed -H 'Content-Type: application/json' -d '{"text":"Village taps supply dirty water"}'
curl -X POST http://localhost:8001/transcribe -H 'Content-Type: application/json' -d '{"audio_base64":"UklGRg==","mime_type":"audio/wav","language":"en"}'
curl -X POST http://localhost:8001/draft-proposal -H 'Content-Type: application/json' -d '{"challenge":{"title":"Unsafe drinking water","description":"Village taps supply dirty water","category":"WATER_SANITATION"},"university":{"name":"Example University","expertise":["water treatment"]}}'
```

Run tests with `pytest` from this directory.
