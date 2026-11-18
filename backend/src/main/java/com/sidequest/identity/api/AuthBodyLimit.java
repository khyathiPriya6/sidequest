package com.sidequest.identity.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice(assignableTypes = AuthController.class)
public class AuthBodyLimit extends RequestBodyAdviceAdapter {
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        byte[] body = input.getBody().readNBytes(8193);
        if (body.length > 8192) throw new AuthFailure(413, "request_too_large", "Authentication request exceeds 8 KiB.");
        return new HttpInputMessage() {
            public ByteArrayInputStream getBody() { return new ByteArrayInputStream(body); }
            public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
}
