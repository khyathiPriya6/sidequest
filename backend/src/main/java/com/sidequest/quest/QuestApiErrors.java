package com.sidequest.quest;

import com.sidequest.identity.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = QuestController.class)
class QuestApiErrors {
    private final ObjectProvider<QuestService> quests;

    QuestApiErrors(ObjectProvider<QuestService> quests) {
        this.quests = quests;
    }

    @ExceptionHandler(QuestApiFailure.class)
    ResponseEntity<ProblemDetail> failure(QuestApiFailure failure, HttpServletRequest request) {
        ProblemDetail problem = problem(failure.status, failure.code, failure.getMessage(), request);
        if (!failure.errors.isEmpty()) {
            problem.setProperty("errors", failure.errors.stream().map(error -> Map.of(
                    "field", error.field(), "code", error.code(), "message", error.message())).toList());
        }
        var response = ResponseEntity.status(failure.status);
        if (failure.etag != null) response.eTag(failure.etag);
        return response.body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "malformed_request",
                "Provide a valid JSON request with only supported fields.", request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> invalidParameter(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "invalid_parameter",
                "Provide a valid path or query parameter.", request));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> concurrentUpdate(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = ResponseEntity.status(HttpStatus.PRECONDITION_FAILED);
            QuestService service = quests.getIfAvailable();
            if (service != null && principal != null && exception.getIdentifier() instanceof UUID id) {
                service.currentEtag(principal.userId(), id).ifPresent(response::eTag);
            }
        return response.body(problem(HttpStatus.PRECONDITION_FAILED, "precondition_failed",
                "The quest changed after it was read. Fetch the latest version before retrying.", request));
    }

    private static ProblemDetail problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
