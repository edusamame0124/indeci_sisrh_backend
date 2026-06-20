package com.indeci.rrhh.subsidio.formula;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.SubsidioBaseDetalle;

/**
 * Contexto de evaluación del DSL declarativo de subsidios (P0-F2).
 * Operadores whitelist: + - * / MIN MAX TOP ROUND DIVIDE SUM_TOP ref.
 */
public record SubsidioFormulaContext(
        List<SubsidioBaseDetalle> baseDetalles,
        Map<String, BigDecimal> parametros,
        BigDecimal topeMensual,
        int mesesEvaluados) {

    public BigDecimal ref(String codigo) {
        BigDecimal valor = parametros.get(codigo);
        if (valor == null) {
            throw new NegocioException("Parámetro de fórmula no disponible: " + codigo);
        }
        return valor;
    }
}
