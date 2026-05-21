package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.ParametroRemunerativo;
import com.indeci.rrhh.repository.ParametroRemunerativoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 010 — Resuelve el valor vigente de un parámetro remunerativo.
 *
 * Estrategia de búsqueda:
 *   1. Si se provee {@code regimenLaboralId}, busca primero por (codigo, anio, regimen).
 *   2. Si no encuentra (o no se provee), busca el parámetro global (regimen=null).
 *   3. Si tampoco existe → lanza {@link NegocioException}.
 *
 * No se cachean valores en memoria: el cálculo de planilla es esporádico y los
 * parámetros pueden cambiar a media migración (corrección normativa).
 */
@Service
@RequiredArgsConstructor
public class ParametroRemunerativoService {

    private final ParametroRemunerativoRepository repository;

    public BigDecimal obtenerValor(String codigo, int anio, Long regimenLaboralId) {
        Optional<ParametroRemunerativo> hit = Optional.empty();

        if (regimenLaboralId != null) {
            hit = repository.findVigenteByRegimen(codigo, anio, regimenLaboralId);
        }
        if (hit.isEmpty()) {
            hit = repository.findVigenteGlobal(codigo, anio);
        }

        return hit
                .map(ParametroRemunerativo::getValorNumerico)
                .orElseThrow(() -> new NegocioException(
                        "Parámetro remunerativo no encontrado: "
                                + codigo + " (año=" + anio
                                + ", regimenLaboralId=" + regimenLaboralId + ")"));
    }

    public Optional<BigDecimal> obtenerValorOpcional(String codigo, int anio, Long regimenLaboralId) {
        Optional<ParametroRemunerativo> hit = Optional.empty();
        if (regimenLaboralId != null) {
            hit = repository.findVigenteByRegimen(codigo, anio, regimenLaboralId);
        }
        if (hit.isEmpty()) {
            hit = repository.findVigenteGlobal(codigo, anio);
        }
        return hit.map(ParametroRemunerativo::getValorNumerico);
    }
}
