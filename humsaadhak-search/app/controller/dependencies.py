"""Shared by every controller: the service-token check and access to the services wired in app.main."""
import secrets

from fastapi import Header, HTTPException, Request, status

from app.service.feedback_service import FeedbackService
from app.service.index_service import IndexService
from app.service.match_service import MatchService
from app.service.search_service import SearchService


def require_token(request: Request, authorization: str = Header(default="")) -> None:
    expected = request.app.state.settings.service_token
    if not authorization.startswith("Bearer ") or not secrets.compare_digest(authorization[7:], expected):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid service token")


def search_service(request: Request) -> SearchService: return request.app.state.search
def match_service(request: Request) -> MatchService: return request.app.state.match
def index_service(request: Request) -> IndexService: return request.app.state.indexing
def feedback_service(request: Request) -> FeedbackService: return request.app.state.feedback
