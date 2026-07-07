package com.indeci.rrhh.service.cts;

import com.indeci.exception.CtsNoAplicableException;
import com.indeci.rrhh.service.ParametroRemunerativoService;
import com.indeci.rrhh.service.cts.strategy.Cts276Strategy;
import com.indeci.rrhh.service.cts.strategy.CtsServirStrategy;
import com.indeci.rrhh.service.cts.strategy.CtsStrategyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CtsStrategyFactoryTest {

    private final ParametroRemunerativoService param = Mockito.mock(ParametroRemunerativoService.class);
    private final CtsStrategyFactory factory = new CtsStrategyFactory(
            List.of(new CtsServirStrategy(param), new Cts276Strategy(param)));

    @Test
    void resuelve_servir() {
        assertEquals("CTSSERVIR", factory.resolver("SERVIR").estrategiaCodigo());
        assertEquals("CTSSERVIR", factory.resolver("30057").estrategiaCodigo());
    }

    @Test
    void resuelve_276() {
        assertEquals("CTS276", factory.resolver("276").estrategiaCodigo());
    }

    @Test
    void regimen_no_habilitado_lanza_excepcion() {
        assertThrows(CtsNoAplicableException.class, () -> factory.resolver("728"));
        assertThrows(CtsNoAplicableException.class, () -> factory.resolver("1057"));
    }
}
