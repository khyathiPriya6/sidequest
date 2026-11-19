package com.sidequest.identity.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

class CookiePolicyTest {
    @Test void onlyExplicitLocalProfileUsesHttpCookie() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        var serializer = new SecurityConfiguration().cookieSerializer(environment);
        var response = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieValue(new MockHttpServletRequest(), response, "test"));
        assertThat(response.getHeader("Set-Cookie")).startsWith("sidequest-local-id=")
                .contains("HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Secure", "Domain=");
        environment.setActiveProfiles("local", "production");
        assertThatThrownBy(() -> new SecurityConfiguration().cookieSerializer(environment))
                .isInstanceOf(IllegalStateException.class);
    }
}
