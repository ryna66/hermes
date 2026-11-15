import os


AI_MODE = os.getenv("AI_MODE", "mock").lower()
EMBEDDING_DIMENSIONS = int(os.getenv("EMBEDDING_DIMENSIONS", "3"))
CLASSIFICATION_PROVIDER = os.getenv("CLASSIFICATION_PROVIDER", "")
EMBEDDING_PROVIDER = os.getenv("EMBEDDING_PROVIDER", "")
TRANSCRIPTION_PROVIDER = os.getenv("TRANSCRIPTION_PROVIDER", "")
DRAFTING_PROVIDER = os.getenv("DRAFTING_PROVIDER", "")

if AI_MODE not in {"mock", "real"}:
    raise ValueError("AI_MODE must be 'mock' or 'real'")
if EMBEDDING_DIMENSIONS < 1:
    raise ValueError("EMBEDDING_DIMENSIONS must be positive")
