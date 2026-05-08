package com.indeci.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

import com.indeci.exception.GlobalExceptionHandler;
import com.indeci.security.ratelimit.LoginRateLimiter;
import com.indeci.telemetry.controller.ClientTelemetryController;
import com.indeci.telemetry.service.ClientTelemetryService;
import com.indeci.util.ClientInfoUtil;

@ExtendWith(MockitoExtension.class)
class ClientTelemetryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClientTelemetryService telemetryService;

    @Mock
    private ClientInfoUtil clientInfoUtil;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void setUp() {
        ClientTelemetryController controller = new ClientTelemetryController(telemetryService, clientInfoUtil);
        GlobalExceptionHandler advice = new GlobalExceptionHandler(loginRateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice).build();
    }

    @Test
    void postClient_acepta202() throws Exception {
        when(clientInfoUtil.obtenerIpReal(any())).thenReturn("127.0.0.1");
        when(clientInfoUtil.obtenerUserAgent(any())).thenReturn("JUnit");

        mockMvc.perform(post("/api/telemetry/client")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"TEST_UI\"}"))
                .andExpect(status().isAccepted());

        verify(telemetryService).registrar(any(), any(), any());
    }
}
