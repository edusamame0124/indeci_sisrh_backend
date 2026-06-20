package com.indeci.rrhh.service.subsidio;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.subsidio.SubsidioValidacionDto;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioCitt;
import com.indeci.rrhh.entity.SubsidioLiquidacion;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.entity.SubsidioValidacionRegistro;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.repository.SubsidioCittRepository;
import com.indeci.rrhh.repository.SubsidioLiquidacionRepository;
import com.indeci.rrhh.repository.SubsidioTramoRepository;
import com.indeci.rrhh.repository.SubsidioValidacionRegistroRepository;
import com.indeci.rrhh.subsidio.SubsidioEstados;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioValidacionService {

    private final SubsidioValidacionRegistroRepository validacionRepository;
    private final SubsidioTramoRepository tramoRepository;
    private final SubsidioLiquidacionRepository liquidacionRepository;
    private final SubsidioCasoRepository casoRepository;
    private final SubsidioCittRepository cittRepository;
    private final PeriodoPlanillaRepository periodoRepository;

    @Transactional(readOnly = true)
    public List<SubsidioValidacionDto> validarCaso(Long casoId) {
        List<SubsidioValidacionDto> result = new ArrayList<>();
        SubsidioCaso caso = casoRepository.findByIdAndActivo(casoId, 1).orElse(null);
        if (caso == null) {
            return result;
        }
        List<SubsidioTramo> tramos = tramoRepository
                .findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(casoId, 1, "S");
        result.addAll(validarTramosSolapados(casoId, tramos));
        result.addAll(validarCittCobertura(casoId, tramos));
        if (SubsidioEstados.MODO_SIMULACION.equals(caso.getModoCalculo())) {
            result.add(dto("SUB_V013", SubsidioEstados.SEVERIDAD_INFORMATIVA,
                    "Está viendo un cálculo de simulación; no afecta planilla oficial.", casoId, null, null));
        }
        return result;
    }

    @Transactional
    public List<SubsidioValidacionDto> validarTramosSolapados(
            Long casoId, List<SubsidioTramo> tramos) {
        List<SubsidioValidacionDto> bloqueos = new ArrayList<>();
        List<SubsidioTramo> lista = tramos.isEmpty()
                ? tramoRepository.findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(
                        casoId, 1, "S")
                : tramos;
        for (int i = 0; i < lista.size(); i++) {
            for (int j = i + 1; j < lista.size(); j++) {
                SubsidioTramo a = lista.get(i);
                SubsidioTramo b = lista.get(j);
                if (!a.getFechaHasta().isBefore(b.getFechaDesde())
                        && !b.getFechaHasta().isBefore(a.getFechaDesde())) {
                    bloqueos.add(registrar("SUB_V001", SubsidioEstados.SEVERIDAD_BLOQUEO,
                            "Los tramos mensuales se superponen. Ajuste fechas antes de calcular.",
                            casoId, null, null));
                    return bloqueos;
                }
            }
        }
        return bloqueos;
    }

    @Transactional(readOnly = true)
    public void assertPeriodoAbierto(String periodoPlanilla) {
        Optional<PeriodoPlanilla> pp = periodoRepository.findByPeriodoAndActivo(periodoPlanilla, 1);
        if (pp.isPresent() && "CERRADO".equalsIgnoreCase(pp.get().getEstado())) {
            throw new NegocioException(
                    "No puede aplicar subsidio: el periodo de planilla está cerrado. (SUB_V003)");
        }
    }

    @Transactional(readOnly = true)
    public void assertLiquidacionNoAplicada(SubsidioLiquidacion liq) {
        if (SubsidioEstados.LIQ_APLICADO_PLANILLA.equals(liq.getEstado())) {
            throw new NegocioException(
                    "La liquidación ya fue aplicada a planilla. Use ajuste o reversión autorizada. (SUB_V014)");
        }
    }

    @Transactional
    public SubsidioValidacionDto registrar(
            String codigo, String severidad, String mensaje,
            Long casoId, Long tramoId, Long liquidacionId) {
        SubsidioValidacionRegistro reg = new SubsidioValidacionRegistro();
        reg.setCasoId(casoId);
        reg.setTramoId(tramoId);
        reg.setLiquidacionId(liquidacionId);
        reg.setCodigoValidacion(codigo);
        reg.setSeveridad(severidad);
        reg.setMensajeUsuario(mensaje);
        reg.setResuelta("N");
        reg.setCreatedAt(LocalDateTime.now());
        validacionRepository.save(reg);
        return dto(codigo, severidad, mensaje, casoId, tramoId, liquidacionId);
    }

    private List<SubsidioValidacionDto> validarCittCobertura(
            Long casoId, List<SubsidioTramo> tramos) {
        List<SubsidioValidacionDto> alertas = new ArrayList<>();
        List<SubsidioCitt> citts = cittRepository.findByCasoIdAndActivoOrderByFechaInicioAsc(casoId, 1);
        if (citts.isEmpty()) {
            alertas.add(registrar("SUB_V010", SubsidioEstados.SEVERIDAD_ALERTA,
                    "El CITT está pendiente de validación documental.", casoId, null, null));
            return alertas;
        }
        for (SubsidioTramo tramo : tramos) {
            boolean cubierto = citts.stream().anyMatch(c ->
                    !c.getFechaFin().isBefore(tramo.getFechaDesde())
                            && !c.getFechaInicio().isAfter(tramo.getFechaHasta()));
            if (!cubierto) {
                alertas.add(registrar("SUB_V002", SubsidioEstados.SEVERIDAD_BLOQUEO,
                        "El CITT no cubre el periodo liquidado. Verifique fechas del certificado.",
                        casoId, tramo.getId(), null));
            }
        }
        return alertas;
    }

    private static SubsidioValidacionDto dto(
            String codigo, String severidad, String mensaje,
            Long casoId, Long tramoId, Long liquidacionId) {
        return new SubsidioValidacionDto(
                codigo, severidad, mensaje, casoId, tramoId, liquidacionId);
    }
}
