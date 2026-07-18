package com.indeci.rrhh.vinculacion.importacion.reglas;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.indeci.rrhh.vinculacion.importacion.ReglaVinculacion;
import com.indeci.rrhh.vinculacion.importacion.RowIssue;
import com.indeci.rrhh.vinculacion.importacion.TextoNormalizador;
import com.indeci.rrhh.vinculacion.importacion.VinculacionColumna;
import com.indeci.rrhh.vinculacion.importacion.VinculacionRowRaw;

/**
 * Datos propios del vínculo: código AIRHSP, número de contrato y monto contratado.
 *
 * <p>El AIRHSP es de 6 dígitos, pero el Excel lo trae con basura de formato
 * (caso real: {@code '´000104'}) o leído como número sin sus ceros a la izquierda.
 * Aquí se sanea antes de exigir el formato, en vez de rechazar la fila.
 *
 * <p>El monto es lo <b>pactado en contrato</b>; el sueldo básico lo deriva el motor
 * (monto + incrementos D.S.), por eso no se importa.
 */
@Component
public class VinculoDatosRule implements ReglaVinculacion {

    private static final int AIRHSP_LONGITUD = 6;

    @Override
    public int orden() {
        return 30;
    }

    @Override
    public List<RowIssue> validar(VinculacionRowRaw fila) {
        final List<RowIssue> issues = new ArrayList<>();
        validarAirhsp(fila, issues);
        validarNumeroContrato(fila, issues);
        validarMonto(fila, issues);
        return issues;
    }

    private void validarAirhsp(VinculacionRowRaw fila, List<RowIssue> issues) {
        final String original = fila.texto(VinculacionColumna.CODIGO_AIRHSP);
        if (original == null) {
            issues.add(RowIssue.error(VinculacionColumna.CODIGO_AIRHSP, "Código AIRHSP de la plaza vacío."));
            return;
        }
        final String digitos = TextoNormalizador.soloDigitos(original);
        if (digitos == null) {
            issues.add(RowIssue.error(VinculacionColumna.CODIGO_AIRHSP,
                    "Código AIRHSP sin dígitos: '" + original + "'."));
            return;
        }
        if (digitos.length() > AIRHSP_LONGITUD) {
            issues.add(RowIssue.error(VinculacionColumna.CODIGO_AIRHSP,
                    "Código AIRHSP de " + digitos.length() + " dígitos: se esperan " + AIRHSP_LONGITUD + "."));
            return;
        }
        final String saneado = TextoNormalizador.padCeros(digitos, AIRHSP_LONGITUD);
        if (!saneado.equals(original)) {
            issues.add(RowIssue.saneado(VinculacionColumna.CODIGO_AIRHSP,
                    "Código AIRHSP saneado: '" + original + "' → '" + saneado + "'."));
        }
    }

    private void validarNumeroContrato(VinculacionRowRaw fila, List<RowIssue> issues) {
        if (fila.texto(VinculacionColumna.NUMERO_CONTRATO) == null) {
            issues.add(RowIssue.error(VinculacionColumna.NUMERO_CONTRATO,
                    "N° de contrato vacío: junto al DNI es la llave que decide si el vínculo se crea o se actualiza."));
        }
    }

    private void validarMonto(VinculacionRowRaw fila, List<RowIssue> issues) {
        final Object crudo = fila.crudo(VinculacionColumna.MONTO_CONTRATO);
        final BigDecimal monto = fila.numero(VinculacionColumna.MONTO_CONTRATO);
        if (monto == null) {
            issues.add(RowIssue.error(VinculacionColumna.MONTO_CONTRATO,
                    "Monto contratado vacío o ilegible"
                            + (crudo != null ? ": '" + crudo + "'." : ".")));
            return;
        }
        if (monto.signum() <= 0) {
            issues.add(RowIssue.error(VinculacionColumna.MONTO_CONTRATO,
                    "Monto contratado debe ser mayor a cero (valor: " + monto.toPlainString() + ")."));
            return;
        }
        if (crudo instanceof String) {
            issues.add(RowIssue.saneado(VinculacionColumna.MONTO_CONTRATO,
                    "Monto interpretado desde texto: '" + crudo + "' → " + monto.toPlainString() + "."));
        }
    }
}
