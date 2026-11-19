package com.sidequest.quest;

import java.util.List;
import org.springframework.http.HttpStatus;

final class QuestApiFailure extends RuntimeException {
    final HttpStatus status;
    final String code;
    final List<FieldError> errors;
    final String etag;

    private QuestApiFailure(HttpStatus status, String code, String message,
            List<FieldError> errors, String etag) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
        this.etag = etag;
    }

    static QuestApiFailure badRequest(String code, String message) {
        return new QuestApiFailure(HttpStatus.BAD_REQUEST, code, message, List.of(), null);
    }

    static QuestApiFailure notFound() {
        return new QuestApiFailure(HttpStatus.NOT_FOUND, "not_found",
                "The requested resource was not found.", List.of(), null);
    }

    static QuestApiFailure conflict() {
        return new QuestApiFailure(HttpStatus.CONFLICT, "invalid_state",
                "The quest cannot be changed in its current state.", List.of(), null);
    }

    static QuestApiFailure validation(List<FieldError> errors) {
        return new QuestApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed",
                "Correct the invalid quest fields.", List.copyOf(errors), null);
    }

    static QuestApiFailure preconditionRequired() {
        return new QuestApiFailure(HttpStatus.PRECONDITION_REQUIRED, "precondition_required",
                "Send the current quest ETag in the If-Match header.", List.of(), null);
    }

    static QuestApiFailure preconditionFailed(String etag) {
        return new QuestApiFailure(HttpStatus.PRECONDITION_FAILED, "precondition_failed",
                "The quest changed after it was read. Fetch the latest version before retrying.",
                List.of(), etag);
    }

    record FieldError(String field, String code, String message) { }
}
