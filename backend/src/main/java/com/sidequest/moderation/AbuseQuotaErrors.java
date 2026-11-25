package com.sidequest.moderation;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Applies to all API controllers so rate-limit failures cannot become generic 500 errors. */
@RestControllerAdvice
public class AbuseQuotaErrors {
    @ExceptionHandler(AccountAbuseQuota.QuotaExceededException.class)
    ResponseEntity<ProblemDetail> onExhausted(
            AccountAbuseQuota.QuotaExceededException exception, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "rate_limited");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "3600")
                .body(problem);
    }
}
