package com.indeci.rrhh.service.evento.importacion;

import java.time.LocalDateTime;

import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.ImportResultDto;

/**
 * V012_42 F2 — Estado mutable y thread-safe de un job de import histórico de eventos. Mismo
 * patrón que {@code AsistenciaImportJob} (paquete propio, no compartido — dominio distinto).
 */
public class EventoHistoricoImportJob {

    public static final String EN_COLA = "EN_COLA";
    public static final String PROCESANDO = "PROCESANDO";
    public static final String COMPLETADO = "COMPLETADO";
    public static final String ERROR = "ERROR";

    private final String jobId;
    private final LocalDateTime creadoEn = LocalDateTime.now();

    private volatile String estado = EN_COLA;
    private volatile int porcentaje = 0;
    private volatile String fase = "En cola";
    private volatile ImportResultDto resultado;
    private volatile String error;
    private volatile LocalDateTime finalizadoEn;

    public EventoHistoricoImportJob(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFinalizadoEn() {
        return finalizadoEn;
    }

    /** Monotónico, topado en 99 — el 100 lo fija exclusivamente {@link #completar}. */
    public void avanzar(int porcentaje, String fase) {
        this.estado = PROCESANDO;
        this.porcentaje = Math.max(this.porcentaje, Math.min(porcentaje, 99));
        this.fase = fase;
    }

    public void completar(ImportResultDto resultado) {
        this.resultado = resultado;
        this.porcentaje = 100;
        this.fase = "Importación completada";
        this.estado = COMPLETADO;
        this.finalizadoEn = LocalDateTime.now();
    }

    public void fallar(String mensaje) {
        this.error = mensaje;
        this.fase = "Error";
        this.estado = ERROR;
        this.finalizadoEn = LocalDateTime.now();
    }

    public EventoHistoricoImportJobDto toDto() {
        return new EventoHistoricoImportJobDto(jobId, estado, porcentaje, fase, resultado, error);
    }
}
