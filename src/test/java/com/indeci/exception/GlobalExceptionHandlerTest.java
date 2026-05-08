package com.indeci.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.indeci.security.ratelimit.LoginRateLimiter;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void rateLimit_devuelve429() {
        when(loginRateLimiter.obtenerIntentos("127.0.0.1")).thenReturn(5);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = handler.handleRateLimit(
                new RateLimitExceededException("Demasiados intentos, intenta luego"),
                request);

        assertEquals(429, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(429, body.get("status"));
        assertEquals("Demasiados intentos, intenta luego", body.get("mensaje"));
    }
}
