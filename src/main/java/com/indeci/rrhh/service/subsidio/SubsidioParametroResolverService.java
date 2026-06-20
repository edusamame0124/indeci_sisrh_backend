package com.indeci.rrhh.service.subsidio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.entity.SubsidioParametro;
import com.indeci.rrhh.entity.SubsidioParametroVersion;
import com.indeci.rrhh.repository.SubsidioParametroRepository;
import com.indeci.rrhh.repository.SubsidioParametroVersionRepository;
import com.indeci.rrhh.service.ParametroRemunerativoService;

import lombok.RequiredArgsConstructor;

/**
 * Resuelve parámetros versionados del módulo subsidios con fallback al catálogo general.
 */
@Service
@RequiredArgsConstructor
public class SubsidioParametroResolverService {

    private static final Map<String, String> FALLBACK_GENERAL = Map.of(
            "UIT_REF", "UIT",
            "BIM_PCT_CAS", "SUBSIDIO_TOPE_PCT_UIT",
            "DIVISOR_PROMEDIO", "SUBSIDIO_DIVISOR_PROMEDIO",
            "DIAS_ENTIDAD_ENF", "SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD",
            "DIAS_ENTIDAD_MAT", "SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD");

    private final SubsidioParametroRepository parametroRepository;
    private final SubsidioParametroVersionRepository versionRepository;
    private final ParametroRemunerativoService parametroRemunerativoService;

    @Transactional(readOnly = true)
    public BigDecimal obtenerNumerico(String codigo, LocalDate fecha, int anioFiscal) {
        Optional<SubsidioParametro> param = parametroRepository.findByCodigoAndActivo(codigo, 1);
        if (param.isPresent()) {
            Optional<SubsidioParametroVersion> ver = versionRepository.findVigente(
                    param.get().getId(), fecha);
            if (ver.isPresent() && ver.get().getValorNumerico() != null) {
                return ver.get().getValorNumerico();
            }
        }
        String fallback = FALLBACK_GENERAL.get(codigo);
        if (fallback != null) {
            return parametroRemunerativoService.obtenerValor(fallback, anioFiscal, null);
        }
        return BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> mapaNumerico(LocalDate fecha, int anioFiscal) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (String codigo : List.of(
                "BIM_PCT_CAS", "DIVISOR_PROMEDIO", "DIAS_ENTIDAD_ENF",
                "DIAS_ENTIDAD_MAT", "DIAS_MATERNIDAD_REG", "GAP_CITT_DIAS")) {
            map.put(codigo, obtenerNumerico(codigo, fecha, anioFiscal));
        }
        BigDecimal uit = obtenerNumerico("UIT_REF", fecha, anioFiscal);
        if (uit.signum() <= 0) {
            uit = parametroRemunerativoService.obtenerValor("UIT", anioFiscal, null);
        }
        map.put("UIT_REF", uit);
        map.put("TOPE_MENSUAL", uit.multiply(map.get("BIM_PCT_CAS")).setScale(2, java.math.RoundingMode.HALF_UP));
        return map;
    }

    @Transactional(readOnly = true)
    public List<Long> idsVersionVigente(LocalDate fecha) {
        return parametroRepository.findAll().stream()
                .filter(p -> p.getActivo() != null && p.getActivo() == 1)
                .map(p -> versionRepository.findVigente(p.getId(), fecha).map(SubsidioParametroVersion::getId).orElse(null))
                .filter(id -> id != null)
                .toList();
    }
}
