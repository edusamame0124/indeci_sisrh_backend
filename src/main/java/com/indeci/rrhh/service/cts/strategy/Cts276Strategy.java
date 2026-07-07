package com.indeci.rrhh.service.cts.strategy;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.service.ParametroRemunerativoService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Feature 016 / US2 — CTS D.Leg. 276 (Carrera Administrativa).
 *
 * <p>Base = remuneración principal (MUC) al cese, tomada de
 * {@code EmpleadoPlanilla.sueldoBasico}. Excluye aguinaldos y fracciones
 * extraordinarias por construcción.</p>
 *
 * <p><b>Directiva defensiva (cero magic numbers):</b> el factor por año
 * (100% / 50% / escalonado) NUNCA es literal — se consume de
 * {@code CTS_FACTOR_ANUAL_276} vía {@link ParametroRemunerativoService}, de modo
 * que RR.HH. lo ajuste desde BD sin recompilar (REGLA-02).</p>
 */
@Component
@RequiredArgsConstructor
public class Cts276Strategy implements CtsStrategy {

    private static final Set<String> CODIGOS = Set.of("276");
    private static final String PARAM_FACTOR = "CTS_FACTOR_ANUAL_276";

    private final ParametroRemunerativoService parametroService;

    @Override
    public boolean soporta(String regimenCodigo) {
        return regimenCodigo != null && CODIGOS.contains(regimenCodigo.trim().toUpperCase());
    }

    @Override
    public String estrategiaCodigo() {
        return "CTS276";
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
