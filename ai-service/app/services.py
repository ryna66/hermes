import base64
import hashlib
from abc import ABC, abstractmethod

from . import settings
from .schemas import (
    Category, ClassifyRequest, ClassifyResponse, DraftProposalRequest,
    DraftProposalResponse, EmbedResponse, TranscribeRequest, TranscribeResponse,
)


class Classifier(ABC):
    @abstractmethod
    def classify(self, request: ClassifyRequest) -> ClassifyResponse: ...


class Embedder(ABC):
    @abstractmethod
    def embed(self, text: str) -> EmbedResponse: ...


class Transcriber(ABC):
    @abstractmethod
    def transcribe(self, request: TranscribeRequest) -> TranscribeResponse: ...


class ProposalDrafter(ABC):
    @abstractmethod
    def draft(self, request: DraftProposalRequest) -> DraftProposalResponse: ...


class MockClassifier(Classifier):
    def classify(self, request):
        text = f"{request.title} {request.description}".lower()
        rules = {
            "WATER_SANITATION": ("water", "sanitation", "sewage", "toilet", "tap"),
            "HEALTH": ("health", "hospital", "clinic", "disease", "medical"),
            "EDUCATION": ("school", "education", "teacher", "classroom", "student"),
            "AGRICULTURE": ("farm", "crop", "agriculture", "irrigation", "farmer"),
            "ENVIRONMENT": ("pollution", "environment", "waste", "forest", "climate"),
            "INFRASTRUCTURE": ("road", "bridge", "electricity", "infrastructure", "drain"),
        }
        category: Category = next((cat for cat, words in rules.items() if any(w in text for w in words)), "OTHER")
        urgency = "CRITICAL" if any(w in text for w in ("urgent", "life-threatening", "collapse")) else "HIGH" if any(w in text for w in ("unsafe", "severe", "emergency")) else "MEDIUM" if category != "OTHER" else "LOW"
        return ClassifyResponse(category=category, urgency=urgency, confidence=0.94 if category != "OTHER" else 0.55)


class MockEmbedder(Embedder):
    def embed(self, text):
        dims = settings.EMBEDDING_DIMENSIONS
        seed = hashlib.sha256(text.encode("utf-8")).digest()
        values = []
        for i in range(dims):
            chunk = hashlib.sha256(seed + i.to_bytes(4, "big")).digest()
            values.append(round(int.from_bytes(chunk[:4], "big") / (2**32 - 1) * 2 - 1, 6))
        return EmbedResponse(embedding=values, model=f"mock-hash-v1-{dims}", dimensions=dims, confidence=1.0)


class MockTranscriber(Transcriber):
    def transcribe(self, request):
        try:
            base64.b64decode(request.audio_base64, validate=True)
        except Exception as exc:
            raise ValueError("audio_base64 must contain valid base64 audio") from exc
        return TranscribeResponse(text="Village taps supply dirty water", language=request.language, confidence=0.91)


class MockProposalDrafter(ProposalDrafter):
    def draft(self, request):
        expertise = ", ".join(request.university.expertise)
        return DraftProposalResponse(proposal=f"{request.university.name} will address {request.challenge.title} ({request.challenge.category}) through a needs assessment, a pilot solution informed by {expertise}, and measured community outcomes.", confidence=0.86)


class NotConfigured:
    def __init__(self, capability, provider):
        self.capability, self.provider = capability, provider or "unset"

    def _raise(self):
        raise NotImplementedError(f"{self.capability} provider '{self.provider}' is not configured")

    classify = embed = transcribe = draft = lambda self, *args: self._raise()


def services():
    if settings.AI_MODE == "mock":
        return MockClassifier(), MockEmbedder(), MockTranscriber(), MockProposalDrafter()
    return (NotConfigured("LLM classification", settings.CLASSIFICATION_PROVIDER),
            NotConfigured("sentence-transformers embeddings", settings.EMBEDDING_PROVIDER),
            NotConfigured("Whisper transcription", settings.TRANSCRIPTION_PROVIDER),
            NotConfigured("LLM proposal drafting", settings.DRAFTING_PROVIDER))
