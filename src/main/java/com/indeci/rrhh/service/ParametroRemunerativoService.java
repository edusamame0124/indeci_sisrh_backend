package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.ParametroRemunerativo;
import com.indeci.rrhh.repository.ParametroRemunerativoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 010 / F1 Motor v3 — Resuelve el valor vigente de un parámetro
 * remunerativo.
 *
 * Tiene dos APIs paralelas:
 *
 *   1. Por año fiscal — {@link #obtenerValor(String, int, Long)} y
 *      {@link #obtenerValorOpcional(String, int, Long)}. Es la API original;
 *      asume un parámetro único por año/régimen. Sigue siendo usada por el
 *      motor existente (paso 5/8/9/12 actuales).
 *
 *   2. Por fecha de devengue — {@link #obtenerValorEnPeriodo(String, String,
 *      Long)} y {@link #obtenerValorEnFecha(String, LocalDate, Long)}. Soporta
 *      C4 RRHH (2026-05-31): parámetros con vigencia mensual o submensual
 *      (ej. TOPE_SEGURO_AFP cambia en mayo 2026). Los nuevos pasos del motor
 *      v3 (F1.3+) DEBEN usar esta API.
 *
 * Estrategia de búsqueda en ambos APIs:
 *   1. Si se provee {@code regimenLaboralId}, busca primero por régimen.
 *   2. Si no encuentra, busca el parámetro global (regimen=null).
 *   3. Si tampoco existe → lanza {@link NegocioException} en la versión
 *      no-opcional, o Optional.empty en la opcional.
 *
 * No se cachean valores en memoria: el cálculo de planilla es esporádico y los
 * parámetros pueden cambiar a media migración (corrección normativa).
 */
@Service
@RequiredArgsConstructor
public class ParametroRemunerativoService {

    private final ParametroRemunerativoRepository repository;

    // ======================================================================
    // API original por año fiscal (compatibilidad — no romper motor existente)
    // ======================================================================

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

    // ======================================================================
    // F1.3a — API por fecha de devengue (Motor v3 / decisión C4 RRHH)
    // ======================================================================

    /**
     * Resuelve el parámetro vigente para una fecha de devengue concreta.
     * Buscando: FECHA_VIG_INI ≤ fecha ≤ NVL(FECHA_VIG_FIN, +∞).
     *
     * @throws NegocioException si no hay parámetro vigente para esa fecha.
     */
    public BigDecimal obtenerValorEnFecha(String codigo, LocalDate fechaDevengue, Long regimenLaboralId) {
        Optional<ParametroRemunerativo> hit = Optional.empty();

        if (regimenLaboralId != null) {
            hit = repository.findVigenteByRegimenEnFecha(codigo, regimenLaboralId, fechaDevengue);
        }
        if (hit.isEmpty()) {
            hit = repository.findVigenteGlobalEnFecha(codigo, fechaDevengue);
        }

        return hit
                .map(ParametroRemunerativo::getValorNumerico)
                .orElseThrow(() -> new NegocioException(
                        "Parámetro remunerativo no vigente: "
                                + codigo + " (fecha=" + fechaDevengue
                                + ", regimenLaboralId=" + regimenLaboralId + ")"));
    }

    /**
     * Variante opcional del lookup por fecha (no lanza si falta).
     */
    public Optional<BigDecimal> obtenerValorOpcionalEnFecha(String codigo, LocalDate fechaDevengue, Long regimenLaboralId) {
        Optional<ParametroRemunerativo> hit = Optional.empty();

        if (regimenLaboralId != null) {
            hit = repository.findVigenteByRegimenEnFecha(codigo, regimenLaboralId, fechaDevengue);
        }
        if (hit.isEmpty()) {
            hit = repository.findVigenteGlobalEnFecha(codigo, fechaDevengue);
        }
        return hit.map(ParametroRemunerativo::getValorNumerico);
    }

    /**
     * Conveniencia: deriva la fecha de devengue del período (primer día del mes)
     * y delega en {@link #obtenerValorEnFecha}.
     *
     * @param periodo formato "YYYYMM" (ej. "202605").
     */
    public BigDecimal obtenerValorEnPeriodo(String codigo, String periodo, Long regimenLaboralId) {
        return obtenerValorEnFecha(codigo, periodoToFechaInicio(periodo), regimenLaboralId);
    }

    public Optional<BigDecimal> obtenerValorOpcionalEnPeriodo(String codigo, String periodo, Long regimenLaboralId) {
        return obtenerValorOpcionalEnFecha(codigo, periodoToFechaInicio(periodo), regimenLaboralId);
    }

    /**
     * Convierte un período al primer día del mes (fecha canónica de devengue).
     * Acepta ambos formatos usados en el sistema:
     * <ul>
     *   <li>{@code "YYYYMM"} — canónico de BD (ej. "202605").</li>
     *   <li>{@code "YYYY-MM"} — formato amigable de UI/tests (ej. "2026-05").</li>
     * </ul>
     */
    public static LocalDate periodoToFechaInicio(String periodo) {
        if (periodo == null) {
            throw new NegocioException("Período inválido (esperado YYYYMM o YYYY-MM): null");
        }
        String p = periodo.replace("-", "").trim();
        if (p.length() != 6) {
            throw new NegocioException(
                    "Período inválido (esperado YYYYMM o YYYY-MM): " + periodo);
        }
        try {
            int anio = Integer.parseInt(p.substring(0, 4));
            int mes  = Integer.parseInt(p.substring(4, 6));
            return LocalDate.of(anio, mes, 1);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw new NegocioException(
                    "Período inválido (esperado YYYYMM o YYYY-MM): " + periodo);
        }
    }
}
