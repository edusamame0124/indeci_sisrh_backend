package com.indeci.telemetry.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.telemetry.dto.ClientTelemetryRequest;
import com.indeci.telemetry.service.ClientTelemetryService;
import com.indeci.util.ClientInfoUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class ClientTelemetryController {

    private final ClientTelemetryService telemetryService;
    private final ClientInfoUtil clientInfoUtil;

    @PostMapping("/client")
    public ResponseEntity<Void> recibir(
            @Valid @RequestBody ClientTelemetryRequest body,
            HttpServletRequest httpRequest) {

        String ip = clientInfoUtil.obtenerIpReal(httpRequest);
        String ua = clientInfoUtil.obtenerUserAgent(httpRequest);
        telemetryService.registrar(body, ip, ua);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
