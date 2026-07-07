package com.indeci.rrhh.service.cts;

import com.indeci.exception.CtsNoAplicableException;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Feature 016 / US3 — Poka-Yoke de dominio: el régimen CAS (D.Leg. 1057) NO
 * contempla CTS. Rechaza cualquier intento de liquidación con mensaje normativo.
 */
@Component
public class CtsCasGuard {

    private static final Set<String> CAS = Set.of("CAS", "1057");

    public void verificar(String regimenCodigo) {
        if (regimenCodigo != null && CAS.contains(regimenCodigo.trim().toUpperCase())) {
            throw new CtsNoAplicableException(
                    "El régimen laboral CAS 1057 no contempla el beneficio de CTS según normativa vigente");
        }
    }
}
