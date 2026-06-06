package com.indeci.rrhh.service;


import com.indeci.exception.ConceptoNoAsignableManualmenteException;
import com.indeci.exception.ConceptoRegimenNoAplicableException;
import com.indeci.exception.ConceptoYaAsignadoException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ConceptoPlanillaResponseDto;
import com.indeci.rrhh.dto.ConceptosAsignablesDto;
import com.indeci.rrhh.dto.EmpleadoConceptoDto;
import com.indeci.rrhh.dto.EmpleadoConceptoResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.service.support.RegimenAplicableHelper;

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

    /**
     * Mejora 2026-06-03 — Conceptos de remuneración base: el motor los calcula
     * desde {@code EmpleadoPlanilla.sueldoBasico} (Configuración de planilla), por
     * lo que NO se asignan a mano. CAS=00501, 728=00301, 276=00102(+00101 legacy);
     * SERVIR L001–L004 (RD0111-2021-EF).
     */
    private static final Set<String> MEF_BASE_REMUNERATIVA = Set.of(
            "00101", "00102", "00301", "00501",
            "L001", "L002", "L003", "L004");

    private final EmpleadoConceptoRepository repository;

    private final ConceptoPlanillaRepository
            conceptoRepository;

    // Régimen del empleado para filtrar/validar conceptos (mejora 2026-06-03).
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final ConceptoPlanillaService conceptoPlanillaService;

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

        // VALIDACIÓN 1b (mejora 2026-06-03): el concepto debe aplicar al régimen
        // del empleado (guard normativo — evita pagos indebidos por DS de otro régimen).
        validarRegimenAplica(concepto, resolverRegimenEmpleado(dto.getEmpleadoId()));

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
            validarRegimenAplica(concepto, resolverRegimenEmpleado(entity.getEmpleadoId()));
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

    /**
     * Conceptos asignables a un empleado, filtrados por su régimen laboral
     * (mejora 2026-06-03). Usa la MISMA regla que el motor/preflight
     * ({@link RegimenAplicableHelper}) para que el dropdown y la validación no
     * diverjan. Si el empleado no tiene planilla/régimen, devuelve régimen null
     * y la lista sin filtro de régimen (la UI pedirá configurar la planilla).
     */
    public ConceptosAsignablesDto listarAsignables(Long empleadoId) {
        String regimen = resolverRegimenEmpleado(empleadoId);
        ConceptosAsignablesDto out = new ConceptosAsignablesDto();
        out.setRegimenLaboral(regimen);
        List<ConceptoPlanillaResponseDto> conceptos = conceptoPlanillaService.listar()
                .stream()
                .filter(c -> RegimenAplicableHelper.aplica(c.getRegimenAplicable(), regimen))
                // Excluir los que calcula el motor (aportes, ESSALUD, 5ta, asig.
                // familiar) y la remuneración base (viene de Configuración planilla).
                .filter(c -> c.getCodigoMef() == null
                        || !(MEF_AUTOCALCULADOS.contains(c.getCodigoMef())
                                || MEF_BASE_REMUNERATIVA.contains(c.getCodigoMef())))
                .collect(Collectors.toList());
        out.setConceptos(conceptos);
        return out;
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
    /** Régimen laboral (código) del empleado, de su planilla activa; null si no tiene. */
    private String resolverRegimenEmpleado(Long empleadoId) {
        if (empleadoId == null) {
            return null;
        }
        return empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .filter(java.util.Objects::nonNull)
                .flatMap(regimenLaboralRepository::findById)
                .map(rl -> rl.getCodigo())
                .orElse(null);
    }

    /**
     * Guard normativo (mejora 2026-06-03): rechaza asignar un concepto cuyo
     * {@code REGIMEN_APLICABLE} no aplica al régimen del empleado (alias CAS≡1057).
     * Si el empleado no tiene régimen, no se contradice (no bloquea).
     */
    private void validarRegimenAplica(ConceptoPlanilla concepto, String regimenEmpleado) {
        if (!RegimenAplicableHelper.aplica(concepto.getRegimenAplicable(), regimenEmpleado)) {
            throw new ConceptoRegimenNoAplicableException(
                    concepto.getCodigoMef(),
                    concepto.getNombre(),
                    regimenEmpleado,
                    concepto.getRegimenAplicable());
        }
    }

    private boolean esCalculadoPorMotor(ConceptoPlanilla concepto) {
        String tipo = concepto.getTipoConcepto();
        if (tipo != null) {
            String t = tipo.toUpperCase();
            if (t.equals("APORTE_TRABAJADOR") || t.equals("APORTE_EMPLEADOR")) {
                return true;
            }
        }
        String mef = concepto.getCodigoMef();
        return mef != null
                && (MEF_AUTOCALCULADOS.contains(mef) || MEF_BASE_REMUNERATIVA.contains(mef));
    }
}