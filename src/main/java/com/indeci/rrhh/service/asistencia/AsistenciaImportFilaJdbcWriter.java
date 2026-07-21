package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Volcado masivo de {@code INDECI_ASISTENCIA_IMPORTACION_FILA} con
 * {@link JdbcTemplate#batchUpdate} en lotes de {@value #BATCH_SIZE}.
 *
 * <p>Reemplaza a {@code JpaRepository.saveAll()} para esta tabla temporal: con 8000+ filas,
 * saveAll acumula todas las entidades en el persistence context (memoria + overhead de flush).
 * El batch de JDBC reutiliza un solo {@code PreparedStatement} y envía por bloques → milisegundos.
 *
 * <p><b>Transacción:</b> este método NO abre transacción propia. Debe invocarse dentro de un
 * método {@code @Transactional} del llamador: así, si un lote falla (p. ej. una restricción de BD),
 * toda la carga —incluida la cabecera de importación— se revierte de forma atómica.
 */
@Component
@RequiredArgsConstructor
public class AsistenciaImportFilaJdbcWriter {

    private final JdbcTemplate jdbcTemplate;

    /** Tamaño de lote del batch (500 = buen balance memoria/round-trips para Oracle). */
    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
            INSERT INTO GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA
              (IMPORTACION_ID, NUMERO_FILA, LINEA_ORIGINAL, DNI, FECHA, NOMBRE_CSV, NOMBRE_SISTEMA,
               DIA_SEMANA, ENTRADA_PROG, SALIDA_PROG, MARCA1, MARCA2, MARCA3, MARCA4, TARDANZA_RAW,
               TARDANZA_MIN, REFRIGERIO_MIN, EXCESO_REFRIG_MIN, TIEMPO_REFRIG_MIN, TIEMPO_ANTES_SAL_MIN,
               HORAS_TRAB_MIN, HORAS_EXTRA_25_MIN, HORAS_EXTRA_35_MIN, HORAS_EXTRA_100_MIN,
               HORAS_EXTRA_TOTAL_MIN, OBSERVACION_MARCADOR, MENSAJE_VALIDACION, ESTADO_FILA,
               ACEPTADA_OBSERVADA, EMPLEADO_ID, HASH_ARCHIVO, USUARIO_IMPORTACION, FECHA_IMPORTACION,
               ERRORES_JSON)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    /**
     * Inserta {@code filas} en lotes de {@value #BATCH_SIZE}. Tras cada lote invoca
     * {@code onBatch(insertadasAcumuladas, total)} para reportar progreso real (usado por el job
     * asíncrono). {@code onBatch} puede ser {@code null} (sin reporte).
     */
    public void insertarLote(List<AsistenciaImportacionFila> filas, BiConsumer<Integer, Integer> onBatch) {
        if (filas == null || filas.isEmpty()) {
            return;
        }
        final int total = filas.size();
        for (int i = 0; i < total; i += BATCH_SIZE) {
            final List<AsistenciaImportacionFila> lote = filas.subList(i, Math.min(i + BATCH_SIZE, total));

            jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public int getBatchSize() {
                    return lote.size();
                }

                @Override
                public void setValues(PreparedStatement ps, int idx) throws SQLException {
                    AsistenciaImportacionFila f = lote.get(idx);
                    int c = 1;
                    ps.setObject(c++, f.getImportacionId());
                    ps.setObject(c++, f.getNumeroFila());
                    ps.setString(c++, f.getLineaOriginal());
                    ps.setString(c++, f.getDni());
                    ps.setObject(c++, f.getFecha());              // LocalDate → DATE (ojdbc8+)
                    ps.setString(c++, f.getNombreCsv());
                    ps.setString(c++, f.getNombreSistema());
                    ps.setString(c++, f.getDiaSemana());
                    ps.setString(c++, f.getEntradaProg());
                    ps.setString(c++, f.getSalidaProg());
                    ps.setString(c++, f.getMarca1());
                    ps.setString(c++, f.getMarca2());
                    ps.setString(c++, f.getMarca3());
                    ps.setString(c++, f.getMarca4());
                    ps.setString(c++, f.getTardanzaRaw());
                    ps.setObject(c++, f.getTardanzaMin());        // Integer nullable → NULL
                    ps.setObject(c++, f.getRefrigerioMin());
                    ps.setObject(c++, f.getExcesoRefrigMin());
                    ps.setObject(c++, f.getTiempoRefrigMin());
                    ps.setObject(c++, f.getTiempoAntesSalMin());
                    ps.setObject(c++, f.getHorasTrabMin());
                    ps.setObject(c++, f.getHorasExtra25Min());
                    ps.setObject(c++, f.getHorasExtra35Min());
                    ps.setObject(c++, f.getHorasExtra100Min());
                    ps.setObject(c++, f.getHorasExtraTotalMin());
                    ps.setString(c++, f.getObservacionMarcador());
                    ps.setString(c++, f.getMensajeValidacion());
                    ps.setString(c++, f.getEstadoFila());         // NOT NULL
                    ps.setObject(c++, f.getAceptadaObservada());
                    ps.setObject(c++, f.getEmpleadoId());
                    ps.setString(c++, f.getHashArchivo());
                    ps.setString(c++, f.getUsuarioImportacion());
                    ps.setObject(c++, f.getFechaImportacion());   // LocalDateTime → TIMESTAMP
                    ps.setString(c++, f.getErroresJson());
                }
            });

            if (onBatch != null) {
                onBatch.accept(Math.min(i + BATCH_SIZE, total), total);
            }
        }
    }
}
