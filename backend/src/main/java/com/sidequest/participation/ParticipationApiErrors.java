package com.sidequest.participation;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {ParticipationController.class, PlayerProfileController.class})
class ParticipationApiErrors {
    @ExceptionHandler(ParticipationFailure.class)
    ResponseEntity<ProblemDetail> failure(ParticipationFailure error, HttpServletRequest request) {
        return problem(error.status, error.code, error.getMessage(), request);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> parameter(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_parameter", "Provide a valid path or query parameter.", request);
    }
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ProblemDetail> database(HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "integrity_error", "The operation could not be completed.", request);
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}
