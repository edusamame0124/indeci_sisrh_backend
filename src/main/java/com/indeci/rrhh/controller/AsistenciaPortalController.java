package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.service.AsistenciaPdfService;
import com.indeci.rrhh.service.AsistenciaService;
import com.indeci.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal/asistencia")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AsistenciaPortalController {

    private final AsistenciaService asistenciaService;
    private final AsistenciaPdfService asistenciaPdfService;

    @GetMapping("/{periodo}")
    public ApiResponse<AsistenciaResponseDto> propia(@PathVariable String periodo) {
        Long empleadoId = empleadoActual();
        return new ApiResponse<>("OK", "Asistencia del periodo",
                asistenciaService.obtener(empleadoId, periodo));
    }

    @GetMapping("/{periodo}/pdf")
    public ResponseEntity<byte[]> pdfPropio(@PathVariable String periodo) {
        Long empleadoId = empleadoActual();
        byte[] pdf = asistenciaPdfService.generar(empleadoId, periodo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("mi-asistencia-" + periodo + ".pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private Long empleadoActual() {
        Long empleadoId = SecurityUtil.getEmpleadoId();
        if (empleadoId == null) {
            throw new NegocioException("La cuenta no tiene empleado vinculado para consultar asistencia.");
        }
        return empleadoId;
    }
}
