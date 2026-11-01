package org.humsaadhak.shared.exception;

/** A business rule was broken; the message is safe to show to the user. Mapped to HTTP 422. */
public class DomainException extends RuntimeException {
    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
