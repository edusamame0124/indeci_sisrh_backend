package com.indeci.telemetry.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.telemetry.dto.ClientTelemetryRequest;
import com.indeci.telemetry.entity.ClientTelemetry;
import com.indeci.telemetry.repository.ClientTelemetryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTelemetryService {

    private final ClientTelemetryRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void registrar(ClientTelemetryRequest request, String ip, String userAgent) {
        ClientTelemetry row = new ClientTelemetry();
        row.setCategory(request.getCategory().trim());
        row.setPayloadJson(toJsonSafe(request));
        row.setIp(truncate(ip, 128));
        row.setUserAgent(truncate(userAgent, 512));
        row.setCreatedAt(LocalDateTime.now());
        repository.save(row);
    }

    private String toJsonSafe(ClientTelemetryRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.warn("Serialización telemetría falló; se guarda categoría solamente", e);
            return "{\"category\":\"" + request.getCategory() + "\"}";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
