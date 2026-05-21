package com.indeci.rrhh.service;


import com.indeci.exception.ConceptoNoAsignableManualmenteException;
import com.indeci.exception.ConceptoYaAsignadoException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoConceptoDto;
import com.indeci.rrhh.dto.EmpleadoConceptoResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpleadoConceptoService {

    /**
     * CODIGO_MEF que el motor calcula automáticamente y, por tanto, NO pueden
     * asignarse a mano. Espeja {@code GeneradorPlanillaService.MEF_AUTOCALCULADOS}
     * (constante privada allí) y {@code EstimacionNetoService}. Sincronización
     * manual — ver deuda técnica Etapa 3 (motor remunerativos).
     */
    private static final Set<String> MEF_AUTOCALCULADOS = Set.of(
            "00302", "00502",                   // asignación familiar 728 / CAS
            "05001", "05002", "05003", "05004", // aporte ONP / AFP + comisión + prima
            "05101",                            // retención 5ta categoría
            "06001", "06002", "05309",          // ESSALUD sin/con EPS + copago EPS
            "05401", "05402");                  // descuento tardanza / falta

    private final EmpleadoConceptoRepository repository;

    private final ConceptoPlanillaRepository
            conceptoRepository;

    public void guardar(
            EmpleadoConceptoDto dto) {

        // Spec 013 / C1 — VALIDACIÓN 1: solo conceptos asignables manualmente.
        // El motor calcula aportes, ESSALUD, 5ta, asig. familiar y descuento de
        // asistencia; asignarlos a mano duplicaría montos (regla relajada — el
        // sueldo básico REMUNERATIVO sí se permite, ver deuda técnica Etapa 3).
        ConceptoPlanilla concepto = conceptoRepository
                .findById(dto.getConceptoPlanillaId())
                .orElseThrow(() -> new NegocioException(
                        "Concepto no existe: id=" + dto.getConceptoPlanillaId()));
        if (esCalculadoPorMotor(concepto)) {
            throw new ConceptoNoAsignableManualmenteException(concepto.getNombre());
        }

        // Spec 013 / C1 — VALIDACIÓN 2: sin duplicados activos del concepto.
        if (repository.existsByEmpleadoIdAndConceptoPlanillaIdAndActivo(
                dto.getEmpleadoId(), dto.getConceptoPlanillaId(), 1)) {
            throw new ConceptoYaAsignadoException();
        }

        EmpleadoConcepto nuevo =
                new EmpleadoConcepto();

        nuevo.setEmpleadoId(
                dto.getEmpleadoId());

        nuevo.setConceptoPlanillaId(
                dto.getConceptoPlanillaId());

        nuevo.setMonto(
                dto.getMonto());

        nuevo.setPorcentaje(
                dto.getPorcentaje());

        nuevo.setFormula(
                dto.getFormula());

        nuevo.setFechaInicio(
                dto.getFechaInicio());

        nuevo.setFechaFin(
                dto.getFechaFin());

        nuevo.setActivo(1);

        repository.save(nuevo);
    }

    public void actualizar(Long id, EmpleadoConceptoDto dto) {
        EmpleadoConcepto entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException(
                        "Concepto asignado no encontrado"));

        if (entity.getActivo() == null || entity.getActivo() != 1) {
            throw new NegocioException("Concepto asignado no encontrado");
        }

        if (dto.getEmpleadoId() != null
                && !dto.getEmpleadoId().equals(entity.getEmpleadoId())) {
            throw new NegocioException(
                    "El empleado del concepto no coincide con el registro");
        }

        Long conceptoId = dto.getConceptoPlanillaId() != null
                ? dto.getConceptoPlanillaId()
                : entity.getConceptoPlanillaId();

        if (conceptoId != null
                && !conceptoId.equals(entity.getConceptoPlanillaId())) {
            ConceptoPlanilla concepto = conceptoRepository.findById(conceptoId)
                    .orElseThrow(() -> new NegocioException(
                            "Concepto no existe: id=" + conceptoId));
            if (esCalculadoPorMotor(concepto)) {
                throw new ConceptoNoAsignableManualmenteException(
                        concepto.getNombre());
            }
            if (repository.existsByEmpleadoIdAndConceptoPlanillaIdAndActivoAndIdNot(
                    entity.getEmpleadoId(), conceptoId, 1, id)) {
                throw new ConceptoYaAsignadoException();
            }
            entity.setConceptoPlanillaId(conceptoId);
        }

        entity.setMonto(dto.getMonto());
        entity.setPorcentaje(dto.getPorcentaje());
        entity.setFormula(dto.getFormula());
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());

        repository.save(entity);
    }

    public void eliminar(Long id) {
        EmpleadoConcepto entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException(
                        "Concepto asignado no encontrado"));

        if (entity.getActivo() == null || entity.getActivo() != 1) {
            throw new NegocioException("Concepto asignado no encontrado");
        }

        entity.setActivo(0);
        repository.save(entity);
    }

    public List<EmpleadoConceptoResponseDto>
    listarEmpleado(Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivo(
                        empleadoId,
                        1)
                .stream()
                .map(this::convertir)
                .collect(Collectors.toList());
    }

    private EmpleadoConceptoResponseDto
    convertir(EmpleadoConcepto e) {

        EmpleadoConceptoResponseDto dto =
                new EmpleadoConceptoResponseDto();

        dto.setId(e.getId());

        dto.setConceptoPlanillaId(
                e.getConceptoPlanillaId());

        dto.setMonto(
                e.getMonto());

        dto.setPorcentaje(
                e.getPorcentaje());

        dto.setFormula(
                e.getFormula());

        dto.setFechaInicio(
                e.getFechaInicio());

        dto.setFechaFin(
                e.getFechaFin());

        dto.setActivo(
                e.getActivo());

        if (e.getConceptoPlanillaId() != null) {

            ConceptoPlanilla concepto =
                    conceptoRepository
                            .findById(
                                    e.getConceptoPlanillaId())
                            .orElse(null);

            if (concepto != null) {

                dto.setConcepto(
                        concepto.getNombre());
            }
        }

        return dto;
    }

    /**
     * Spec 013 / C1 — VALIDACIÓN 1 (regla relajada). Un concepto lo calcula el
     * motor —y no debe asignarse manualmente— si:
     * <ul>
     *   <li>su {@code TIPO_CONCEPTO} es {@code APORTE_TRABAJADOR} o
     *       {@code APORTE_EMPLEADOR} (ONP/AFP/ESSALUD), o</li>
     *   <li>su {@code CODIGO_MEF} está en {@link #MEF_AUTOCALCULADOS}
     *       (asig. familiar, retención 5ta, copago EPS, tardanza/falta) —
     *       estos tienen tipo REMUNERATIVO/DESCUENTO pero igual los calcula el
     *       motor.</li>
     * </ul>
     * Los REMUNERATIVO/NO_REMUNERATIVO/DESCUENTO restantes SÍ se permiten.
     */
    private boolean esCalculadoPorMotor(ConceptoPlanilla concepto) {
        String tipo = concepto.getTipoConcepto();
        if (tipo != null) {
            String t = tipo.toUpperCase();
            if (t.equals("APORTE_TRABAJADOR") || t.equals("APORTE_EMPLEADOR")) {
                return true;
            }
        }
        String mef = concepto.getCodigoMef();
        return mef != null && MEF_AUTOCALCULADOS.contains(mef);
    }
}