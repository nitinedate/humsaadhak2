"""Errors the services raise; app.main turns them into HTTP responses, so services never depend on the web layer."""


class ServiceError(Exception):
    status = 400


class NotIndexedError(ServiceError):
    """The record searched from is not in the index (usually because it is not approved yet)."""
    status = 404


class InvalidRequestError(ServiceError):
    status = 422
