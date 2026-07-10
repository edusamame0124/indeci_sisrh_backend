package com.indeci.rrhh.job;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.indeci.rrhh.service.AsistenciaImportCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Purga mensual de INDECI_ASISTENCIA_IMPORTACION_FILA (Data Lifecycle).
 *
 * Esta tabla es trazabilidad transitoria del import CSV de asistencia (P0):
 * el resultado real del cálculo vive en INDECI_ASISTENCIA_CABECERA/DETALLE,
 * que NUNCA se purgan (versionado histórico permanente, ver V010_81). Por
 * eso este job solo elimina filas hijas de INDECI_ASISTENCIA_IMPORTACION_FILA
 * y deja intacta la cabecera INDECI_ASISTENCIA_IMPORTACION: INDECI_ASISTENCIA_
 * CABECERA mantiene una FK hacia ella (INDECI_ASIST_CAB_IMPORT_FK, V010_67)
 * que nunca se libera, así que borrar la cabecera de importación violaría esa
 * FK (ORA-02292) para cualquier período aún vigente.
 *
 * Se ejecuta el día 1 de cada mes a las 02:00 AM. Corte de antigüedad, tamaño
 * de lote y tope de seguridad son configurables (application.properties):
 *   asistencia.import.limpieza.retencion-meses (default 6)
 *   asistencia.import.limpieza.tamano-lote     (default 500)
 *   asistencia.import.limpieza.max-lotes       (default 20000)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsistenciaImportCleanupJob {

    private final AsistenciaImportCleanupService cleanupService;

    @Value("${asistencia.import.limpieza.retencion-meses:6}")
    private int retencionMeses;

    @Value("${asistencia.import.limpieza.max-lotes:20000}")
    private int maxLotesPorEjecucion;

    @Scheduled(cron = "0 0 2 1 * ?")
    public void purgarFilasVencidas() {
        LocalDateTime corte = LocalDateTime.now().minusMonths(retencionMeses);
        log.info("Limpieza INDECI_ASISTENCIA_IMPORTACION_FILA: corte={} (retencion={} meses)",
                corte, retencionMeses);

        int totalEliminadas = 0;
        int lotes = 0;
        int eliminadasEnLote;
        do {
            eliminadasEnLote = cleanupService.purgarLote(corte);
            totalEliminadas += eliminadasEnLote;
            lotes++;
        } while (eliminadasEnLote > 0 && lotes < maxLotesPorEjecucion);

        if (lotes >= maxLotesPorEjecucion && eliminadasEnLote > 0) {
            log.warn("Limpieza INDECI_ASISTENCIA_IMPORTACION_FILA detenida por tope de seguridad "
                    + "({} lotes); quedan filas vencidas sin purgar, continuara en la proxima ejecucion.",
                    maxLotesPorEjecucion);
        }

        log.info("Limpieza INDECI_ASISTENCIA_IMPORTACION_FILA finalizada: {} filas eliminadas en {} lote(s).",
                totalEliminadas, lotes);
    }
}
