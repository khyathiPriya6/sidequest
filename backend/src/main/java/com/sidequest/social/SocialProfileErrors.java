package com.sidequest.social;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = SocialProfileController.class)
public class SocialProfileErrors {
    @ExceptionHandler(SocialProfileFailure.class)
    ResponseEntity<ProblemDetail> failure(SocialProfileFailure exception, HttpServletRequest request) {
        return response(exception.status, exception.code, exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpServletRequest request) {
        return response(400, "malformed_request", "Provide valid JSON.", request);
    }

    private static ResponseEntity<ProblemDetail> response(int status, String code, String detail,
            HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}
