package com.indeci.rrhh.vinculacion.importacion.reglas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.indeci.rrhh.vinculacion.importacion.ReglaVinculacion;
import com.indeci.rrhh.vinculacion.importacion.RowIssue;
import com.indeci.rrhh.vinculacion.importacion.VinculacionColumna;
import com.indeci.rrhh.vinculacion.importacion.VinculacionRowRaw;

/**
 * Coherencia de fechas y completitud del cese.
 *
 * <p>El <b>estado del vínculo no se importa</b>: lo deriva
 * {@link com.indeci.rrhh.vinculacion.VinculoEstadoResolver} a partir de estas fechas.
 * Por eso su consistencia es la que sostiene todo el ciclo de vida del vínculo.
 *
 * <p>Cese: si hay fecha de cese, motivo y documento son obligatorios — es la condición
 * que habilita la LBS ({@code VinculoEstadoResolver.habilitaLbs}).
 *
 * <p>La columna "Fecha fin" admite texto en el Excel ({@code "Indeterminado"},
 * {@code "-"}); eso significa contrato sin término previsto → se trata como vacío,
 * no como error.
 */
@Component
public class FechasYCeseRule implements ReglaVinculacion {

    @Override
    public int orden() {
        return 40;
    }

    @Override
    public List<RowIssue> validar(VinculacionRowRaw fila) {
        final List<RowIssue> issues = new ArrayList<>();

        final LocalDate ingreso = fila.fecha(VinculacionColumna.FECHA_INGRESO);
        final LocalDate inicio = fila.fecha(VinculacionColumna.FECHA_INICIO_CONTRATO);
        final LocalDate fin = fila.fecha(VinculacionColumna.FECHA_FIN);
        final LocalDate cese = fila.fecha(VinculacionColumna.FECHA_CESE);

        if (ingreso == null) {
            issues.add(RowIssue.error(VinculacionColumna.FECHA_INGRESO, "Fecha de ingreso a la entidad vacía."));
        }
        if (inicio == null) {
            issues.add(RowIssue.error(VinculacionColumna.FECHA_INICIO_CONTRATO, "Fecha de inicio del contrato vacía."));
        }
        if (ingreso != null && inicio != null && inicio.isBefore(ingreso)) {
            issues.add(RowIssue.error(VinculacionColumna.FECHA_INICIO_CONTRATO,
                    "El inicio del contrato (" + inicio + ") es anterior al ingreso a la entidad (" + ingreso + ")."));
        }
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            issues.add(RowIssue.error(VinculacionColumna.FECHA_FIN,
                    "La fecha fin (" + fin + ") es anterior al inicio del contrato (" + inicio + ")."));
        }

        // Texto no interpretable en la columna de fecha fin = sin término previsto.
        final Object finCrudo = fila.crudo(VinculacionColumna.FECHA_FIN);
        if (fin == null && finCrudo instanceof String texto) {
            issues.add(RowIssue.saneado(VinculacionColumna.FECHA_FIN,
                    "Fecha fin '" + texto + "' no es una fecha: se registra como contrato sin término previsto."));
        }

        validarCese(fila, inicio, cese, issues);
        return issues;
    }

    private void validarCese(VinculacionRowRaw fila, LocalDate inicio, LocalDate cese, List<RowIssue> issues) {
        if (cese == null) {
            return;
        }
        if (inicio != null && cese.isBefore(inicio)) {
            issues.add(RowIssue.error(VinculacionColumna.FECHA_CESE,
                    "La fecha de cese (" + cese + ") es anterior al inicio del contrato (" + inicio + ")."));
        }
        if (fila.texto(VinculacionColumna.MOTIVO_CESE) == null) {
            issues.add(RowIssue.error(VinculacionColumna.MOTIVO_CESE,
                    "Hay fecha de cese pero falta el motivo (obligatorio para habilitar la LBS)."));
        }
        if (fila.texto(VinculacionColumna.DOCUMENTO_CESE) == null) {
            issues.add(RowIssue.error(VinculacionColumna.DOCUMENTO_CESE,
                    "Hay fecha de cese pero falta el documento de sustento (obligatorio para habilitar la LBS)."));
        }
    }
}
