package com.indeci.rrhh.service.cts.strategy;

import com.indeci.rrhh.entity.EmpleadoPlanilla;

import java.math.BigDecimal;

/**
 * Feature 016 — Estrategia de base computable de CTS por régimen (Open/Closed).
 *
 * <p>Cada estrategia resuelve la base legal (VP para SERVIR, MUC para 276) y el
 * factor por año — este último SIEMPRE desde parámetro (REGLA-02), nunca literal.</p>
 */
public interface CtsStrategy {

    /** ¿Aplica esta estrategia al código de régimen del vínculo? */
    boolean soporta(String regimenCodigo);

    /** Código persistido de la estrategia: CTS276 | CTSSERVIR. */
    String estrategiaCodigo();

    /**
     * Base computable del vínculo SIN conceptos no computables (valorización
     * ajustada/priorizada, aguinaldos, extraordinarios): solo la remuneración
     * base del puesto al cese.
     */
    BigDecimal resolverBaseComputable(EmpleadoPlanilla vinculo, int anioFiscal);

    /** Factor de base por año, leído de INDECI_PARAMETRO_REMUNERATIVO (REGLA-02). */
    BigDecimal resolverFactorAnual(int anioFiscal, Long regimenLaboralId);
}
