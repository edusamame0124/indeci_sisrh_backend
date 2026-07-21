package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaImportJobDto;

import java.time.LocalDateTime;

/**
 * Estado mutable y thread-safe de un job de validación de asistencia. Lo escribe el hilo asíncrono
 * (vía {@link #avanzar}, {@link #completar}, {@link #fallar}) y lo lee el hilo del polling
 * (vía {@link #toDto}). Los campos son {@code volatile} para visibilidad entre hilos.
 */
public class AsistenciaImportJob {

    public static final String EN_COLA = "EN_COLA";
    public static final String PROCESANDO = "PROCESANDO";
    public static final String COMPLETADO = "COMPLETADO";
    public static final String ERROR = "ERROR";

    private final String jobId;
    private final LocalDateTime creadoEn = LocalDateTime.now();

    private volatile String estado = EN_COLA;
    private volatile int porcentaje = 0;
    private volatile String fase = "En cola";
    /** Genérico: AsistenciaImportPreviewDto (validar/confirmar) o AsistenciaValidacionBatchDto (cálculo). */
    private volatile Object resultado;
    private volatile String error;
    private volatile LocalDateTime finalizadoEn;

    public AsistenciaImportJob(String jobId) {
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

    /**
     * Avanza el progreso. Monotónico (nunca retrocede) y topado en 99: el 100 lo fija
     * exclusivamente {@link #completar} al terminar, para que el frontend nunca vea 100% antes.
     */
    public void avanzar(int porcentaje, String fase) {
        this.estado = PROCESANDO;
        this.porcentaje = Math.max(this.porcentaje, Math.min(porcentaje, 99));
        this.fase = fase;
    }

    public void completar(Object resultado) {
        this.resultado = resultado;
        this.porcentaje = 100;
        this.fase = "Validación completada";
        this.estado = COMPLETADO;
        this.finalizadoEn = LocalDateTime.now();
    }

    public void fallar(String mensaje) {
        this.error = mensaje;
        this.fase = "Error";
        this.estado = ERROR;
        this.finalizadoEn = LocalDateTime.now();
    }

    public AsistenciaImportJobDto toDto() {
        return new AsistenciaImportJobDto(jobId, estado, porcentaje, fase, resultado, error);
    }
}
