package com.indeci.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.telemetry.dto.ClientTelemetryRequest;
import com.indeci.telemetry.entity.ClientTelemetry;
import com.indeci.telemetry.repository.ClientTelemetryRepository;
import com.indeci.telemetry.service.ClientTelemetryService;

@ExtendWith(MockitoExtension.class)
class ClientTelemetryServiceTest {

    @Mock
    private ClientTelemetryRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ClientTelemetryService service;

    @Test
    void registrar_guardaEntidad() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"category\":\"X\"}");

        ClientTelemetryRequest req = new ClientTelemetryRequest();
        req.setCategory("X");
        service.registrar(req, "127.0.0.1", "JUnit");

        verify(repository).save(any(ClientTelemetry.class));
    }
}
