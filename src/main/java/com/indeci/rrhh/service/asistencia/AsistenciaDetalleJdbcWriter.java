package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.AsistenciaDetalle;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Volcado masivo de {@code INDECI_ASISTENCIA_DETALLE} con {@link JdbcTemplate#batchUpdate} en lotes
 * de {@value #BATCH_SIZE}. Reemplaza a {@code JpaRepository.saveAll()} en el paso "Confirmar" del
 * import (materialización de ~8000 días de asistencia), igual que el writer de filas.
 *
 * <p><b>Transacción:</b> no abre transacción propia — debe correr dentro de un método
 * {@code @Transactional} del llamador. Así, un fallo en cualquier lote revierte toda la confirmación.
 */
@Component
@RequiredArgsConstructor
public class AsistenciaDetalleJdbcWriter {

    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
            INSERT INTO GESTIONRRHH.INDECI_ASISTENCIA_DETALLE
              (CABECERA_ID, DIA, TIPO_DIA, MINUTOS_TARDANZA, OBSERVACION, MARCA_ENTRADA, MARCA_SALIDA,
               MARCA3, MARCA4, HORA_ENTRADA_ESPERADA, MINUTOS_SALIDA_ANTICIPADA, HORAS_TRABAJADAS_MIN,
               HORAS_EXTRA_25_MIN, HORAS_EXTRA_35_MIN, HORAS_EXTRA_100_MIN, HORAS_EXTRA_TOTAL_MIN,
               DIA_SEMANA, ORIGEN, PAPELETA_AUTORIZADA, PAPELETA_MOTIVO_RECHAZO,
               PAPELETA_DECISION_USUARIO, PAPELETA_DECISION_FECHA)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    /** Inserta los detalles en lotes de {@value #BATCH_SIZE}. */
    public void insertarLote(List<AsistenciaDetalle> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            return;
        }
        final int total = detalles.size();
        for (int i = 0; i < total; i += BATCH_SIZE) {
            final List<AsistenciaDetalle> lote = detalles.subList(i, Math.min(i + BATCH_SIZE, total));

            jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public int getBatchSize() {
                    return lote.size();
                }

                @Override
                public void setValues(PreparedStatement ps, int idx) throws SQLException {
                    AsistenciaDetalle d = lote.get(idx);
                    int c = 1;
                    ps.setObject(c++, d.getCabeceraId());
                    ps.setObject(c++, d.getDia());                 // LocalDate → DATE
                    ps.setString(c++, d.getTipoDia());
                    ps.setObject(c++, d.getMinutosTardanza());
                    ps.setString(c++, d.getObservacion());
                    ps.setString(c++, d.getMarcaEntrada());
                    ps.setString(c++, d.getMarcaSalida());
                    ps.setString(c++, d.getMarca3());
                    ps.setString(c++, d.getMarca4());
                    ps.setString(c++, d.getHoraEntradaEsperada());
                    ps.setObject(c++, d.getMinutosSalidaAnticipada());
                    ps.setObject(c++, d.getHorasTrabajadasMin());
                    ps.setObject(c++, d.getHorasExtra25Min());
                    ps.setObject(c++, d.getHorasExtra35Min());
                    ps.setObject(c++, d.getHorasExtra100Min());
                    ps.setObject(c++, d.getHorasExtraTotalMin());
                    ps.setString(c++, d.getDiaSemana());
                    ps.setString(c++, d.getOrigen());
                    ps.setObject(c++, d.getPapeletaAutorizada());
                    ps.setString(c++, d.getPapeletaMotivoRechazo());
                    ps.setString(c++, d.getPapeletaDecisionUsuario());
                    ps.setObject(c++, d.getPapeletaDecisionFecha()); // LocalDateTime → TIMESTAMP
                }
            });
        }
    }
}
