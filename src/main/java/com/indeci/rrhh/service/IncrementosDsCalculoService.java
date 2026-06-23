package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.IncrementoDsItemDto;
import com.indeci.rrhh.dto.IncrementosDsResponseDto;
import com.indeci.rrhh.repository.CondicionLaboralRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.service.support.IncrementosDsAplicabilidadHelper;

import lombok.RequiredArgsConstructor;

/**
 * Calcula incrementos DS mensuales para preview UI (GET /incrementos-ds).
 * Montos desde {@link ParametroRemunerativoService} — REGLA-02.
 */
@Service
@RequiredArgsConstructor
public class IncrementosDsCalculoService {

    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final int ESCALA = 2;

    private static final List<DsParametroDef> PARAMETROS_DS = List.of(
            new DsParametroDef("INCREMENTO_DS_311_2022", "DS 311-2022-EF"),
            new DsParametroDef("INCREMENTO_DS_313_2023", "DS 313-2023-EF"),
            new DsParametroDef("INCREMENTO_DS_265_2024", "DS 265-2024-EF"),
            new DsParametroDef("INCREMENTO_DS_279_2024", "DS 279-2024-EF"),
            new DsParametroDef("INCREMENTO_DS_327_2025", "DS 327-2025-EF")
    );

    private final ParametroRemunerativoService parametroService;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final CondicionLaboralRepository condicionLaboralRepository;

    /**
     * @param fechaReferencia default {@link LocalDate#now()} si es {@code null}
     */
    public IncrementosDsResponseDto calcular(
            Long regimenLaboralId,
            Long condicionLaboralId,
            BigDecimal montoContrato,
            LocalDate fechaReferencia) {

        validarMontoContrato(montoContrato);

        LocalDate fecha = fechaReferencia != null ? fechaReferencia : LocalDate.now();
        BigDecimal monto = escala(montoContrato);

        String regimenCodigo = resolverCodigoRegimen(regimenLaboralId);
        String condicionCodigo = resolverCodigoCondicion(condicionLaboralId);

        if (!IncrementosDsAplicabilidadHelper.aplica(regimenCodigo, condicionCodigo)) {
            return IncrementosDsResponseDto.sinIncrementos(monto);
        }

        List<IncrementoDsItemDto> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (DsParametroDef def : PARAMETROS_DS) {
            BigDecimal valor = parametroService
                    .obtenerValorOpcionalEnFecha(def.codigoParametro(), fecha, null)
                    .orElse(BigDecimal.ZERO)
                    .setScale(ESCALA, REDONDEO);
            items.add(new IncrementoDsItemDto(def.codigoParametro(), def.etiquetaDs(), valor));
            total = total.add(valor);
        }

        total = total.setScale(ESCALA, REDONDEO);
        BigDecimal remuneracion = monto.add(total).setScale(ESCALA, REDONDEO);

        return new IncrementosDsResponseDto(true, monto, List.copyOf(items), total, remuneracion);
    }

    private static void validarMontoContrato(BigDecimal montoContrato) {
        if (montoContrato == null || montoContrato.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Monto contratado debe ser mayor a cero");
        }
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(ESCALA, REDONDEO);
    }

    private String resolverCodigoRegimen(Long regimenLaboralId) {
        if (regimenLaboralId == null) {
            return null;
        }
        return regimenLaboralRepository.findById(regimenLaboralId)
                .map(r -> r.getCodigo())
                .orElse(null);
    }

    private String resolverCodigoCondicion(Long condicionLaboralId) {
        if (condicionLaboralId == null) {
            return null;
        }
        return condicionLaboralRepository.findById(condicionLaboralId)
                .map(c -> c.getCodigo())
                .orElse(null);
    }

    private record DsParametroDef(String codigoParametro, String etiquetaDs) {
    }
}
