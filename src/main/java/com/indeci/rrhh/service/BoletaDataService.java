package com.indeci.rrhh.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;

import com.indeci.rrhh.dto.BoletaPagoResponseDto;
import com.indeci.rrhh.dto.ConceptoBoletaDto;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Persona;
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
        MovimientoPlanilla mov = movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .orElseThrow(() -> new RuntimeException("No se encontró boleta generada para el empleado en el periodo " + periodo));

        Empleado emp = empleadoRepository.findById(empleadoId).orElse(null);
        Persona persona = (emp != null && emp.getPersona() != null) ? emp.getPersona() : null;

        BoletaPagoResponseDto dto = new BoletaPagoResponseDto();
        dto.setPeriodo(mov.getPeriodo());
        
        if (persona != null) {
            dto.setNombreCompleto(persona.getNombreCompleto());
            dto.setDni(persona.getDni());
        }

        // Metadatos Inmutables (snapshots)
        dto.setRegimenLaboral(mov.getRegimenLaboralSnapshot());
        dto.setNivelRemunerativo(mov.getNivelRemunerativoSnapshot());
        dto.setCuentaBancaria(mov.getCuentaBancariaSnapshot());
        dto.setModalidad(mov.getModalidadSnapshot());
        
        // Días Laborados
        Integer dias = 30; // fallback
        // TODO: Mapear diasLaborados si existe en el modelo MovimientoPlanilla. Por ahora usamos 30.
        dto.setDiasLaborados(dias);

        // Detalles
        List<MovimientoPlanillaDetalle> detalles = detalleRepository.findByMovimientoPlanillaId(mov.getId());
        
        dto.setIngresos(mapearConceptos(detalles, "INGRESO"));
        dto.setDescuentos(mapearConceptos(detalles, "DESCUENTO"));
        dto.setAportes(mapearConceptos(detalles, "APORTE"));

        // Totales
        dto.setTotalIngresos(mov.getTotalIngresos());
        dto.setTotalDescuentos(mov.getTotalDescuentos());
        dto.setNetoPagar(mov.getNetoPagar());

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
