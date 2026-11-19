package com.sidequest.participation;

import org.springframework.http.HttpStatus;

final class ParticipationFailure extends RuntimeException {
    final HttpStatus status;
    final String code;
    ParticipationFailure(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
    static ParticipationFailure notFound() {
        return new ParticipationFailure(HttpStatus.NOT_FOUND, "not_found", "The requested resource was not found.");
    }
    static ParticipationFailure badRequest(String code) {
        return new ParticipationFailure(HttpStatus.BAD_REQUEST, code, "Provide valid parameters and no request body for this action.");
    }
    static ParticipationFailure integrity() {
        return new ParticipationFailure(HttpStatus.INTERNAL_SERVER_ERROR, "integrity_error", "The operation could not be completed.");
    }
}
