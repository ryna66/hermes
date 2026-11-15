from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import settings
from .schemas import (
    ClassifyRequest, ClassifyResponse, DraftProposalRequest, DraftProposalResponse,
    EmbedRequest, EmbedResponse, ErrorResponse, HealthResponse, TranscribeRequest,
    TranscribeResponse,
)
from .services import services

app = FastAPI(title="SIH AI Service", version="0.1.0")


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    body = ErrorResponse(error={"code": "INVALID_INPUT", "message": "Request validation failed"})
    return JSONResponse(status_code=422, content=body.model_dump())


@app.exception_handler(NotImplementedError)
async def not_configured_handler(request: Request, exc: NotImplementedError):
    body = ErrorResponse(error={"code": "NOT_CONFIGURED", "message": str(exc)})
    return JSONResponse(status_code=503, content=body.model_dump())


@app.exception_handler(ValueError)
async def invalid_value_handler(request: Request, exc: ValueError):
    body = ErrorResponse(error={"code": "INVALID_INPUT", "message": str(exc)})
    return JSONResponse(status_code=422, content=body.model_dump())


@app.get("/health", response_model=HealthResponse)
def health():
    return {"status": "ok", "mode": settings.AI_MODE, "confidence": 1.0}


@app.post("/classify", response_model=ClassifyResponse)
def classify(body: ClassifyRequest):
    return services()[0].classify(body)


@app.post("/embed", response_model=EmbedResponse)
def embed(body: EmbedRequest):
    return services()[1].embed(body.text)


@app.post("/transcribe", response_model=TranscribeResponse)
def transcribe(body: TranscribeRequest):
    return services()[2].transcribe(body)


@app.post("/draft-proposal", response_model=DraftProposalResponse)
def draft_proposal(body: DraftProposalRequest):
    return services()[3].draft(body)
