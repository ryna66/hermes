from typing import Literal

from pydantic import BaseModel, Field

Category = Literal[
    "WATER_SANITATION", "HEALTH", "EDUCATION", "AGRICULTURE",
    "ENVIRONMENT", "INFRASTRUCTURE", "OTHER",
]


class ConfidenceResponse(BaseModel):
    confidence: float = Field(ge=0, le=1)


class HealthResponse(ConfidenceResponse):
    status: Literal["ok"]
    mode: Literal["mock", "real"]


class ClassifyRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(min_length=1, max_length=10000)


class ClassifyResponse(ConfidenceResponse):
    category: Category
    urgency: Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]


class EmbedRequest(BaseModel):
    text: str = Field(min_length=1, max_length=10000)


class EmbedResponse(ConfidenceResponse):
    embedding: list[float]
    model: str
    dimensions: int = Field(gt=0)


class TranscribeRequest(BaseModel):
    audio_base64: str = Field(min_length=1)
    mime_type: str = Field(min_length=1)
    language: str = Field(min_length=2, max_length=20)


class TranscribeResponse(ConfidenceResponse):
    text: str
    language: str


class ProposalChallenge(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(min_length=1, max_length=10000)
    category: Category


class ProposalUniversity(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    expertise: list[str] = Field(min_length=1)


class DraftProposalRequest(BaseModel):
    challenge: ProposalChallenge
    university: ProposalUniversity


class DraftProposalResponse(ConfidenceResponse):
    proposal: str


class ErrorDetail(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorDetail
    confidence: Literal[0.0] = 0.0
