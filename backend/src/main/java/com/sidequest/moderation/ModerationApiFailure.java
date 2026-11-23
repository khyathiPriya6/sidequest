package com.sidequest.moderation;

import org.springframework.http.HttpStatus;

public final class ModerationApiFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    ModerationApiFailure(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static ModerationApiFailure forbidden() {
        return new ModerationApiFailure(HttpStatus.FORBIDDEN, "moderator_required", "Moderator authorization is required.");
    }
    static ModerationApiFailure notFound() {
        return new ModerationApiFailure(HttpStatus.NOT_FOUND, "not_found", "The requested resource was not found.");
    }
    static ModerationApiFailure invalid() {
        return new ModerationApiFailure(HttpStatus.BAD_REQUEST, "invalid_moderation_request", "Provide a supported moderation action and reason.");
    }
    static ModerationApiFailure conflict() {
        return new ModerationApiFailure(HttpStatus.CONFLICT, "invalid_moderation_state", "The review state has changed. Reload before deciding.");
    }
}
