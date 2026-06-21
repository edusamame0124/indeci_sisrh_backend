package com.indeci.rrhh.subsidio.formula;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.entity.SubsidioBaseDetalle;

class SubsidioFormulaEngineTest {

    private SubsidioFormulaEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SubsidioFormulaEngine(new ObjectMapper());
    }

    @Test
    void subsidioDiario_divide_sumaTop_entre_divisor() {
        SubsidioBaseDetalle d1 = detalle("202501", "3000");
        SubsidioBaseDetalle d2 = detalle("202502", "3200");
        SubsidioFormulaContext ctx = new SubsidioFormulaContext(
                List.of(d1, d2),
                Map.of("DIVISOR_PROMEDIO", new BigDecimal("360")),
                new BigDecimal("2475.00"),
                12);

        String json = """
                {"op":"DIVIDE","args":[
                  {"op":"SUM_TOP","months":12,"field":"baseComputable","cap":"TOPE_MENSUAL"},
                  {"ref":"DIVISOR_PROMEDIO"}
                ],"round":{"mode":"HALF_UP","scale":2}}
                """;

        BigDecimal result = engine.evaluar(json, ctx);
        assertThat(result).isEqualByComparingTo("13.75");
    }

    @Test
    void top_limita_monto_mensual() {
        SubsidioFormulaContext ctx = new SubsidioFormulaContext(
                List.of(), Map.of(), new BigDecimal("100.00"), 12);
        String json = """
                {"op":"TOP","args":[{"op":"ADD","args":[150,50]}]}
                """;
        assertThat(engine.evaluar(json, ctx)).isEqualByComparingTo("100.00");
    }

    private static SubsidioBaseDetalle detalle(String periodo, String monto) {
        SubsidioBaseDetalle d = new SubsidioBaseDetalle();
        d.setPeriodo(periodo);
        d.setBaseComputable(new BigDecimal(monto));
        return d;
    }
}
