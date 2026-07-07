package com.indeci.rrhh.service.cts.strategy;

import com.indeci.exception.CtsNoAplicableException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Feature 016 — Enruta el cálculo de CTS hacia la estrategia del régimen
 * (Open/Closed). Spring inyecta todas las {@link CtsStrategy} disponibles.
 */
@Component
@RequiredArgsConstructor
public class CtsStrategyFactory {

    private final List<CtsStrategy> estrategias;

    /**
     * @throws CtsNoAplicableException si ningún régimen habilitado soporta el
     *         código (p. ej. 728, formativa, o CAS si no lo filtró el guard).
     */
    public CtsStrategy resolver(String regimenCodigo) {
        return estrategias.stream()
                .filter(s -> s.soporta(regimenCodigo))
                .findFirst()
                .orElseThrow(() -> new CtsNoAplicableException(
                        "El régimen laboral '" + regimenCodigo
                                + "' no está habilitado para liquidación de CTS "
                                + "(solo D.Leg. 276 y SERVIR Ley 30057)."));
    }
}
