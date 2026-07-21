package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaImportConfirmRequest;
import com.indeci.rrhh.dto.AsistenciaImportPreviewDto;
import com.indeci.rrhh.service.AsistenciaImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la validación de asistencia en el pool {@code importExecutor} (Opción B). Al terminar,
 * marca el {@link AsistenciaImportJob} como COMPLETADO (con el resultado) o ERROR.
 *
 * <p>Bean separado del servicio para que el proxy de {@code @Async} aplique, y para romper el
 * ciclo de dependencias (el runner conoce al servicio; el servicio NO conoce al runner).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AsistenciaImportAsyncRunner {

    private final AsistenciaImportService importService;

    @Async("importExecutor")
    public void ejecutarPreview(
            AsistenciaImportJob job, String periodo, byte[] bytes, String nombreArchivo, String usuario) {
        try {
            AsistenciaImportPreviewDto preview =
                    importService.previewConProgreso(job, periodo, bytes, nombreArchivo, usuario);
            job.completar(preview);
        } catch (Exception e) {
            fallar(job, "validar", e);
        }
    }

    @Async("importExecutor")
    public void ejecutarConfirmar(
            AsistenciaImportJob job, Long importacionId, AsistenciaImportConfirmRequest request) {
        try {
            AsistenciaImportPreviewDto resultado =
                    importService.confirmarConProgreso(job, importacionId, request);
            job.completar(resultado);
        } catch (Exception e) {
            fallar(job, "confirmar", e);
        }
    }

    @Async("importExecutor")
    public void ejecutarValidarCabeceras(AsistenciaImportJob job, Long importacionId) {
        try {
            job.completar(importService.validarCabecerasConProgreso(job, importacionId));
        } catch (Exception e) {
            fallar(job, "ejecutar-calculo", e);
        }
    }

    private void fallar(AsistenciaImportJob job, String accion, Exception e) {
        Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
        String msg = causa.getMessage() != null
                ? causa.getMessage().split("\n")[0]
                : e.getClass().getSimpleName();
        log.error("[CARGA-DEBUG] Job {} ({}) falló: {}", job.getJobId(), accion, msg, e);
        job.fallar(msg);
    }
}
