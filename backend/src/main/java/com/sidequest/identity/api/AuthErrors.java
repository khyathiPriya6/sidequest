package com.sidequest.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthErrors {
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<ProblemDetail> failure(AuthFailure failure, HttpServletRequest request) {
        var problem = problem(failure.status, failure.code, failure.getMessage(), request);
        if (failure.code.equals("password_length")) problem.setProperty("errors", List.of(Map.of(
                "field", "password", "code", "length", "message", failure.getMessage())));
        return ResponseEntity.status(failure.status).body(problem);
    }
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized(HttpServletRequest request) {
        return problem(401, "unauthenticated", "Authentication is required.", request);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail duplicate(HttpServletRequest request) {
        return problem(409, "account_conflict", "An account could not be created with these details.", request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var problem = problem(422, "validation_failed", "Correct the invalid fields.", request);
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream().map(error ->
                Map.of("field", error.getField(), "code", "invalid", "message", switch (error.getField()) {
                    case "email" -> "Enter a valid email address of at most 254 characters.";
                    case "username" -> "Use 3–30 letters, digits, or underscores.";
                    case "password" -> "Enter a password of 12–128 Unicode code points.";
                    default -> "Provide a valid value.";
                })).toList());
        return problem;
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed(HttpServletRequest request) {
        return problem(400, "malformed_request", "Provide a valid JSON request.", request);
    }
    private ProblemDetail problem(int status, String code, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
