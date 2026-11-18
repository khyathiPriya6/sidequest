package com.sidequest.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApiSecurityErrors {
    private final ObjectMapper mapper;
    public ApiSecurityErrors(ObjectMapper mapper) { this.mapper = mapper; }
    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        String title = status == 401 ? "Unauthorized" : "Forbidden";
        mapper.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", title,
                "status", status, "detail", status == 401 ? "Authentication is required." : "Request is not permitted.",
                "instance", request.getRequestURI(), "code", code));
    }
}
