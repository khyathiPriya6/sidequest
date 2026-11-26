package com.sidequest.group;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = GroupController.class)
public class GroupErrors {
    @ExceptionHandler(GroupFailure.class)
    ResponseEntity<ProblemDetail> failure(GroupFailure e,HttpServletRequest request) {
        return respond(e.status,e.code,e.getMessage(),request);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> invalidJson(HttpServletRequest request) {
        return respond(400,"malformed_request","Provide valid JSON.",request);
    }
    private static ResponseEntity<ProblemDetail> respond(int status,String code,String message,HttpServletRequest req) {
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),message);
        p.setProperty("code",code);p.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(p);
    }
}
