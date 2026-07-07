package com.indeci.rrhh.service.cts.strategy;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.service.ParametroRemunerativoService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Feature 016 / US1 — CTS SERVIR (Ley 30057, Art. 34 + D.S. 040-2014-PCM).
 *
 * <p>Base = 100% de la Valorización Principal (VP) vigente al cese, tomada de
 * {@code EmpleadoPlanilla.sueldoBasico} del vínculo (base por régimen ya vigente
 * en el motor). Se EXCLUYEN por construcción valorización ajustada/priorizada,
 * aguinaldos y extraordinarios (no se suman). El factor por año viene de
 * parámetro (CTS_FACTOR_ANUAL_SERVIR) — nunca literal (REGLA-02).</p>
 */
@Component
@RequiredArgsConstructor
public class CtsServirStrategy implements CtsStrategy {

    private static final Set<String> CODIGOS = Set.of("SERVIR", "30057");
    private static final String PARAM_FACTOR = "CTS_FACTOR_ANUAL_SERVIR";

    private final ParametroRemunerativoService parametroService;

    @Override
    public boolean soporta(String regimenCodigo) {
        return regimenCodigo != null && CODIGOS.contains(regimenCodigo.trim().toUpperCase());
    }

    @Override
    public String estrategiaCodigo() {
        return "CTSSERVIR";
    }

    @Override
    public BigDecimal resolverBaseComputable(EmpleadoPlanilla vinculo, int anioFiscal) {
        return vinculo.getSueldoBasico() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(vinculo.getSueldoBasico());
    }

    @Override
    public BigDecimal resolverFactorAnual(int anioFiscal, Long regimenLaboralId) {
        return parametroService.obtenerValor(PARAM_FACTOR, anioFiscal, null);
    }
}
