package com.indeci.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.indeci.auth.service.AuthService;
import com.indeci.exception.GlobalExceptionHandler;
import com.indeci.security.ratelimit.LoginRateLimiter;
import com.indeci.util.ClientInfoUtil;

@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private ClientInfoUtil clientInfoUtil;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, clientInfoUtil);
        GlobalExceptionHandler advice = new GlobalExceptionHandler(loginRateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice).build();
    }

    @Test
    void logout_sinContenido() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-test-value\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout(any());
    }
}
