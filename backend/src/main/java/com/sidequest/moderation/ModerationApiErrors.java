package com.sidequest.moderation;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ModerationController.class, QuestReportController.class})
class ModerationApiErrors {
    @ExceptionHandler(ModerationApiFailure.class)
    ResponseEntity<ProblemDetail> failure(ModerationApiFailure error, HttpServletRequest request) {
        var detail = ProblemDetail.forStatusAndDetail(error.status(), error.getMessage());
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", error.code());
        return ResponseEntity.status(error.status()).body(detail);
    }
}
