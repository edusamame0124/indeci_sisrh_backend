package com.indeci.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.indeci.auth.service.AuthService;
import com.indeci.exception.GlobalExceptionHandler;
import com.indeci.security.ratelimit.LoginRateLimiter;
import com.indeci.util.ClientInfoUtil;

import jakarta.servlet.http.Cookie;

/**
 * Spec 013 / C4 — el refresh token viaja en cookie HttpOnly.
 *   - logout con cookie → 204, revoca el token y limpia la cookie
 *   - logout sin cookie → 204 tolerante, no llama al servicio
 *   - refresh sin cookie → 403 (no hay sesión que renovar)
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private ClientInfoUtil clientInfoUtil;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    private static final String COOKIE = "sisrh_refresh_token";

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, clientInfoUtil);
        GlobalExceptionHandler advice = new GlobalExceptionHandler(loginRateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice).build();
    }

    @Test
    void logout_conCookie_revocaYLimpiaLaCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie(COOKIE, "refresh-test-value")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout(any());
    }

    @Test
    void logout_sinCookie_esTolerante() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authService, never()).logout(any());
    }

    @Test
    void refresh_sinCookie_devuelve403() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isForbidden());

        verify(authService, never()).refreshToken(any(), any(), any());
    }
}
