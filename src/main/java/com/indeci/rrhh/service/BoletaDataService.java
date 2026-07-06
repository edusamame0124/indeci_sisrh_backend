package com.indeci.rrhh.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.indeci.rrhh.dto.AguinaldoSeccionDto;
import com.indeci.rrhh.dto.BoletaPagoResponseDto;
import com.indeci.rrhh.dto.ConceptoBoletaDto;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.TipoProceso;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;

@Service
@RequiredArgsConstructor
public class BoletaDataService {

    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository detalleRepository;
    private final EmpleadoRepository empleadoRepository;

    @Transactional(readOnly = true)
    public BoletaPagoResponseDto obtenerBoletaData(Long empleadoId, String periodo) {
        // Track B — Consolidación SOLO en presentación (opción A): se cargan todos
        // los movimientos del período y se separan por TipoProceso. Los movimientos
        // regular y AGUINALDO permanecen separados en BD (trazabilidad); aquí se unen.
        List<MovimientoPlanilla> movs =
                movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1);
        if (movs.isEmpty()) {
            throw new RuntimeException(
                    "No se encontró boleta generada para el empleado en el periodo " + periodo);
        }

        // Principal (sección regular): último movimiento NO-AGUINALDO por id — preserva
        // el comportamiento actual (regular/adicional). AGUINALDO va en su sección aparte.
        MovimientoPlanilla principal = movs.stream()
                .filter(m -> TipoProceso.fromTipoPlanilla(m.getTipoPlanilla()) != TipoProceso.AGUINALDO)
                .max(Comparator.comparing(MovimientoPlanilla::getId))
                .orElse(null);
        MovimientoPlanilla aguinaldo = movs.stream()
                .filter(m -> TipoProceso.fromTipoPlanilla(m.getTipoPlanilla()) == TipoProceso.AGUINALDO)
                .max(Comparator.comparing(MovimientoPlanilla::getId))
                .orElse(null);

        MovimientoPlanilla ref = principal != null ? principal : aguinaldo;

        Empleado emp = empleadoRepository.findById(empleadoId).orElse(null);
        Persona persona = (emp != null && emp.getPersona() != null) ? emp.getPersona() : null;

        BoletaPagoResponseDto dto = new BoletaPagoResponseDto();
        dto.setPeriodo(ref.getPeriodo());

        if (persona != null) {
            dto.setNombreCompleto(persona.getNombreCompleto());
            dto.setDni(persona.getDni());
        }

        // Metadatos Inmutables (snapshots) — del movimiento de referencia.
        dto.setRegimenLaboral(ref.getRegimenLaboralSnapshot());
        dto.setNivelRemunerativo(ref.getNivelRemunerativoSnapshot());
        dto.setCuentaBancaria(ref.getCuentaBancariaSnapshot());
        dto.setModalidad(ref.getModalidadSnapshot());

        // ── Sección REGULAR (idéntica a hoy cuando existe el movimiento regular) ──
        if (principal != null) {
            dto.setDiasLaborados(principal.getDiasLaborados() != null ? principal.getDiasLaborados() : 30);
            List<MovimientoPlanillaDetalle> detalles =
                    detalleRepository.findByMovimientoPlanillaId(principal.getId());
            dto.setIngresos(mapearConceptos(detalles, "INGRESO"));
            dto.setDescuentos(mapearConceptos(detalles, "DESCUENTO"));
            dto.setAportes(mapearConceptos(detalles, "APORTE"));
            dto.setTotalIngresos(principal.getTotalIngresos());
            dto.setTotalDescuentos(principal.getTotalDescuentos());
            dto.setNetoPagar(principal.getNetoPagar());
        } else {
            // Caso c — solo aguinaldo: sección regular vacía (no boleta regular del período).
            dto.setIngresos(List.of());
            dto.setDescuentos(List.of());
            dto.setAportes(List.of());
            dto.setTotalIngresos(0d);
            dto.setTotalDescuentos(0d);
            dto.setNetoPagar(0d);
        }

        // ── Sección AGUINALDO (opción A) — solo si existe el movimiento AGUINALDO ──
        double netoAguinaldo = 0d;
        if (aguinaldo != null) {
            List<MovimientoPlanillaDetalle> detA =
                    detalleRepository.findByMovimientoPlanillaId(aguinaldo.getId());
            AguinaldoSeccionDto sec = new AguinaldoSeccionDto();
            sec.setTitulo("AGUINALDO/GRATIFICACIÓN " + aguinaldo.getPeriodo());
            sec.setIngresos(mapearConceptos(detA, "INGRESO"));
            sec.setDescuentos(mapearConceptos(detA, "DESCUENTO"));
            sec.setTotalIngresos(aguinaldo.getTotalIngresos());
            sec.setTotalDescuentos(aguinaldo.getTotalDescuentos());
            sec.setNeto(aguinaldo.getNetoPagar());
            dto.setAguinaldo(sec);
            netoAguinaldo = aguinaldo.getNetoPagar() != null ? aguinaldo.getNetoPagar() : 0d;
        }

        // Neto total del documento = neto regular + neto aguinaldo (redondeo monetario 2 dec).
        double netoRegular = dto.getNetoPagar() != null ? dto.getNetoPagar() : 0d;
        dto.setNetoTotal(Math.round((netoRegular + netoAguinaldo) * 100.0) / 100.0);

        return dto;
    }

    private List<ConceptoBoletaDto> mapearConceptos(List<MovimientoPlanillaDetalle> detalles, String tipo) {
        return detalles.stream()
                .filter(d -> {
                    if ("INGRESO".equals(tipo)) {
                        return "REMUNERATIVO".equalsIgnoreCase(d.getConceptoTipo()) ||
                               "NO_REMUNERATIVO".equalsIgnoreCase(d.getConceptoTipo()) ||
                               "INGRESO".equalsIgnoreCase(d.getConceptoTipo());
                    } else if ("DESCUENTO".equals(tipo)) {
                        return "DESCUENTO".equalsIgnoreCase(d.getConceptoTipo()) || 
                               "RETENCION".equalsIgnoreCase(d.getConceptoTipo()) || 
                               "RETENCION_TRIBUTARIA".equalsIgnoreCase(d.getConceptoTipo()) ||
                               "DESCUENTO_JUDICIAL".equalsIgnoreCase(d.getConceptoTipo()) ||
                               "APORTE_TRABAJADOR".equalsIgnoreCase(d.getConceptoTipo());
                    } else if ("APORTE".equals(tipo)) {
                        return "APORTE_EMPLEADOR".equalsIgnoreCase(d.getConceptoTipo()) ||
                               "APORTE".equalsIgnoreCase(d.getConceptoTipo());
                    }
                    return tipo.equalsIgnoreCase(d.getConceptoTipo());
                })
                .map(d -> {
                    ConceptoBoletaDto dto = new ConceptoBoletaDto();
                    dto.setCodigo(d.getConceptoCodigo());
                    dto.setConcepto(d.getConceptoNombre());
                    dto.setMonto(d.getMonto());
                    dto.setObservacion(d.getObservacion());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
