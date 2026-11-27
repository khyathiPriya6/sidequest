package com.sidequest.team;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=TeamController.class)
public class TeamErrors {
    @ExceptionHandler(TeamService.Failure.class)
    ResponseEntity<ProblemDetail> failure(TeamService.Failure ex,HttpServletRequest request){
        var detail=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(ex.status),ex.getMessage());
        detail.setProperty("code",ex.code);
        detail.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(ex.status).cacheControl(CacheControl.noStore()).body(detail);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpServletRequest request){
        var detail=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid team request JSON.");
        detail.setProperty("code","malformed_request");
        detail.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(detail);
    }
}
