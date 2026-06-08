package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.AsistenciaGuardarDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.service.AsistenciaService;
import com.indeci.rrhh.service.AsistenciaPdfService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/asistencia")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class AsistenciaController {

    private final AsistenciaService service;
    private final AsistenciaPdfService pdfService;

    @GetMapping("/{empleadoId}/{periodo}")
    public ApiResponse<AsistenciaResponseDto> obtener(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        return new ApiResponse<>("OK", "Asistencia del período",
                service.obtener(empleadoId, periodo));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> guardar(@RequestBody AsistenciaGuardarDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Asistencia registrada", null);
    }

    @GetMapping("/{empleadoId}/{periodo}/pdf")
    @PreAuthorize(SisrhSecurityExpressions.PLA_READ)
    public ResponseEntity<byte[]> pdf(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        byte[] pdf = pdfService.generar(empleadoId, periodo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("asistencia-" + empleadoId + "-" + periodo + ".pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
