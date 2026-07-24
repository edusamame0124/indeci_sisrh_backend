package com.indeci.rrhh.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.common.dto.ApiResponse;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportAsyncRunner;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportJob;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportJobDto;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportJobRegistry;
import com.indeci.security.auth.SisrhSecurityExpressions;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/**
 * V012_42 F2 — Importador del Excel histórico "DEDUCCIONES DEL TIEMPO DE SERVICIOS" (hoja
 * "sistema") hacia {@code INDECI_EMPLEADO_EVENTO}. Job asíncrono con progreso por polling —
 * mismo patrón que {@code AsistenciaImportController} (evita timeout de gateway y da barra de
 * progreso real en la UI).
 *
 * <p>Solo Día 0 (carga histórica única): no reemplaza el alta manual de eventos
 * ({@code EventoPeriodoController}) ni la materialización automática desde papeleta — ambas
 * siguen siendo el camino operativo (Día 1).</p>
 */
@RestController
@RequestMapping("/api/rrhh/evento-periodo/importar-historico")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
public class EventoHistoricoImportController {

    private final EventoHistoricoImportJobRegistry jobRegistry;
    private final EventoHistoricoImportAsyncRunner asyncRunner;

    @Operation(
            summary = "Inicia el import histórico de eventos (async) — devuelve jobId",
            description = "Lee la hoja 'sistema' del Excel de RR.HH., resuelve DNI→empleado y "
                    + "MOTIVO→tipo de evento, y materializa cada fila válida en "
                    + "INDECI_EMPLEADO_EVENTO como ESTADO=VALIDADO. Responde al instante con "
                    + "{ jobId }; el progreso se consulta por polling en GET .../job/{jobId}.")
    @PostMapping("/async")
    public ApiResponse<Map<String, String>> importarAsync(
            @RequestParam("archivo") MultipartFile archivo) {
        final byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el archivo Excel.");
        }
        EventoHistoricoImportJob job = jobRegistry.crear();
        asyncRunner.ejecutar(job, bytes);
        return new ApiResponse<>("OK", "Importación histórica iniciada", Map.of("jobId", job.getJobId()));
    }

    @Operation(
            summary = "Progreso del job de import histórico — polling",
            description = "Devuelve { estado, porcentaje, fase, resultado?, error? }.")
    @GetMapping("/job/{jobId}")
    public ApiResponse<EventoHistoricoImportJobDto> jobEstado(@PathVariable String jobId) {
        EventoHistoricoImportJob job = jobRegistry.get(jobId);
        if (job == null) {
            throw new NegocioException("Job no encontrado o expirado. Vuelva a iniciar la importación.");
        }
        return new ApiResponse<>("OK", "Estado del job", job.toDto());
    }
}
