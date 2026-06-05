package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.indeci.rrhh.dto.EmpleadoPlanillaDto;
import com.indeci.rrhh.dto.EmpleadoPlanillaResponseDto;
import com.indeci.rrhh.dto.PlanillaConsolidadaRowDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.TipoContratoRepository;
import com.indeci.rrhh.repository.CondicionLaboralRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPlanillaService {

    private static final String EMP_ACTIVO = "ACTIVO";

    private final EmpleadoPlanillaRepository repository;
    private final AuditoriaContext auditoriaContext;
    // Catálogos para resolver etiquetas en el listado (mejora 2026-06-03).
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final TipoContratoRepository tipoContratoRepository;
    private final CondicionLaboralRepository condicionLaboralRepository;
    // Consolidado de todos los empleados (mejora 2026-06-03).
    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_PLANILLA")
    public void guardar(EmpleadoPlanillaDto dto) {

        // 🔥 VALIDAR SUELDO
        if (dto.getSueldoBasico() == null || dto.getSueldoBasico() <= 0) {
            throw new NegocioException("Sueldo básico inválido");
        }

        // 🔥 SOLO UNA ACTIVA
        Optional<EmpleadoPlanilla> existente =
                repository.findFirstByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        if (existente.isPresent()) {
            throw new NegocioException("Ya existe planilla activa");
        }

        EmpleadoPlanilla entity = new EmpleadoPlanilla();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setSueldoBasico(dto.getSueldoBasico());
        entity.setMovilidad(dto.getMovilidad());
        entity.setAlimentacion(dto.getAlimentacion());
        entity.setTieneAsignacionFamiliar(dto.getTieneAsignacionFamiliar());
        entity.setNumHijos(dto.getNumHijos());
        entity.setDescuentoBanco(dto.getDescuentoBanco());
        entity.setDescuentoInstitucion(dto.getDescuentoInstitucion());
        entity.setRegimenLaboralId(dto.getRegimenLaboralId());
        entity.setTipoContratoId(dto.getTipoContratoId());
        entity.setCondicionLaboralId(dto.getCondicionLaboralId());
        entity.setActivo(1);
        entity.setFechaInicio(LocalDate.now());
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Planilla creada empleado: " + dto.getEmpleadoId());
    }

    // ============================
    // LISTAR
    // ============================
    public List<EmpleadoPlanillaResponseDto> listar(Long empleadoId) {

        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .map(e -> {
                    EmpleadoPlanillaResponseDto dto = new EmpleadoPlanillaResponseDto();
                    dto.setId(e.getId());
                    dto.setSueldoBasico(e.getSueldoBasico());
                    dto.setMovilidad(e.getMovilidad());
                    dto.setAlimentacion(e.getAlimentacion());
                    dto.setTieneAsignacionFamiliar(e.getTieneAsignacionFamiliar());
                    dto.setNumHijos(e.getNumHijos());
                    dto.setActivo(e.getActivo());
                    dto.setDescuentoBanco(e.getDescuentoBanco());
                    dto.setDescuentoInstitucion(e.getDescuentoInstitucion());
                    dto.setRegimenLaboralId(e.getRegimenLaboralId());
                    dto.setTipoContratoId(e.getTipoContratoId());
                    dto.setCondicionLaboralId(e.getCondicionLaboralId());

                    // Etiquetas resueltas para el listado.
                    if (e.getRegimenLaboralId() != null) {
                        regimenLaboralRepository.findById(e.getRegimenLaboralId())
                                .ifPresent(rl -> dto.setRegimenLaboral(rl.getCodigo()));
                    }
                    if (e.getTipoContratoId() != null) {
                        tipoContratoRepository.findById(e.getTipoContratoId())
                                .ifPresent(tc -> dto.setTipoContrato(tc.getNombre()));
                    }
                    if (e.getCondicionLaboralId() != null) {
                        condicionLaboralRepository.findById(e.getCondicionLaboralId())
                                .ifPresent(cl -> dto.setCondicionLaboral(cl.getNombre()));
                    }

                    return dto;
                }).toList();
    }

    // ============================
    // LISTAR CONSOLIDADO (todos los empleados activos) — mejora 2026-06-03
    // ============================
    public List<PlanillaConsolidadaRowDto> listarConsolidado() {
        // Defensa: filtra id/valor nulos antes de toMap (un valor null lanza NPE).
        Map<Long, String> regimenes = regimenLaboralRepository.findAll().stream()
                .filter(r -> r.getId() != null && r.getCodigo() != null)
                .collect(Collectors.toMap(r -> r.getId(), r -> r.getCodigo(), (a, b) -> a));
        Map<Long, String> tipos = tipoContratoRepository.findAll().stream()
                .filter(t -> t.getId() != null && t.getNombre() != null)
                .collect(Collectors.toMap(t -> t.getId(), t -> t.getNombre(), (a, b) -> a));
        Map<Long, String> condiciones = condicionLaboralRepository.findAll().stream()
                .filter(c -> c.getId() != null && c.getNombre() != null)
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getNombre(), (a, b) -> a));
        Map<Long, Persona> personas = personaRepository.findAll().stream()
                .collect(Collectors.toMap(Persona::getId, Function.identity()));
        Map<Long, EmpleadoPlanilla> planillas = repository.findByActivo(1).stream()
                .collect(Collectors.toMap(
                        EmpleadoPlanilla::getEmpleadoId, Function.identity(), (a, b) -> a));

        return empleadoRepository.findByEstado(EMP_ACTIVO).stream().map(emp -> {
            PlanillaConsolidadaRowDto row = new PlanillaConsolidadaRowDto();
            row.setEmpleadoId(emp.getId());
            row.setPersonaId(emp.getPersonaId());
            row.setCodigoInterno(emp.getCodigoInterno());
            Persona p = personas.get(emp.getPersonaId());
            if (p != null) {
                row.setNombreCompleto(p.getNombreCompleto());
                row.setDni(p.getDni());
            }
            EmpleadoPlanilla pl = planillas.get(emp.getId());
            if (pl != null) {
                row.setTieneConfig(true);
                row.setPlanillaId(pl.getId());
                row.setSueldoBasico(pl.getSueldoBasico());
                row.setMovilidad(pl.getMovilidad());
                row.setAlimentacion(pl.getAlimentacion());
                row.setTieneAsignacionFamiliar(pl.getTieneAsignacionFamiliar());
                row.setNumHijos(pl.getNumHijos());
                if (pl.getRegimenLaboralId() != null) {
                    row.setRegimenLaboral(regimenes.get(pl.getRegimenLaboralId()));
                }
                if (pl.getTipoContratoId() != null) {
                    row.setTipoContrato(tipos.get(pl.getTipoContratoId()));
                }
                if (pl.getCondicionLaboralId() != null) {
                    row.setCondicionLaboral(condiciones.get(pl.getCondicionLaboralId()));
                }
            }
            return row;
        }).toList();
    }

    // ============================
    // ACTUALIZAR
    // ============================
    @Auditable(accion = "ACTUALIZAR_PLANILLA")
    public void actualizar(Long id, EmpleadoPlanillaDto dto) {

        EmpleadoPlanilla entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Planilla no encontrada"));

        entity.setSueldoBasico(dto.getSueldoBasico());
        entity.setMovilidad(dto.getMovilidad());
        entity.setAlimentacion(dto.getAlimentacion());
        entity.setTieneAsignacionFamiliar(dto.getTieneAsignacionFamiliar());
        entity.setNumHijos(dto.getNumHijos());
        entity.setDescuentoBanco(dto.getDescuentoBanco());
        entity.setDescuentoInstitucion(dto.getDescuentoInstitucion());
        entity.setRegimenLaboralId(dto.getRegimenLaboralId());
        entity.setTipoContratoId(dto.getTipoContratoId());
        entity.setCondicionLaboralId(dto.getCondicionLaboralId());

        repository.save(entity);

        auditoriaContext.setDetalle("Planilla actualizada ID: " + id);
    }

    // ============================
    // ELIMINAR (LÓGICO)
    // ============================
    @Auditable(accion = "ELIMINAR_PLANILLA")
    public void eliminar(Long id) {

        EmpleadoPlanilla entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Planilla no encontrada"));

        entity.setActivo(0);
        entity.setFechaFin(LocalDate.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Planilla desactivada ID: " + id);
    }
}