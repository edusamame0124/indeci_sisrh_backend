package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.indeci.rrhh.dto.EmpleadoPlanillaDto;
import com.indeci.rrhh.dto.EmpleadoPlanillaResponseDto;
import com.indeci.rrhh.dto.IncrementosDsResponseDto;
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
import com.indeci.rrhh.repository.ModalidadCasRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPlanillaService {

    private static final String EMP_ACTIVO = "ACTIVO";
    private static final Pattern AIRHSP_PATTERN = Pattern.compile("^[0-9]{6}$");
    private static final BigDecimal TOLERANCIA_SUELDO = new BigDecimal("0.01");

    private final EmpleadoPlanillaRepository repository;
    private final AuditoriaContext auditoriaContext;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final TipoContratoRepository tipoContratoRepository;
    private final CondicionLaboralRepository condicionLaboralRepository;
    private final ModalidadCasRepository modalidadCasRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;
    private final IncrementosDsCalculoService incrementosDsCalculoService;

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_PLANILLA")
    public void guardar(EmpleadoPlanillaDto dto) {

        Optional<EmpleadoPlanilla> existente =
                repository.findFirstByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        if (existente.isPresent()) {
            throw new NegocioException("Ya existe planilla activa");
        }

        RemuneracionCalculada remuneracion = calcularRemuneracionDesdeDto(dto);

        EmpleadoPlanilla entity = new EmpleadoPlanilla();
        entity.setEmpleadoId(dto.getEmpleadoId());
        aplicarRemuneracion(entity, remuneracion);
        entity.setTieneAsignacionFamiliar(dto.getTieneAsignacionFamiliar());
        entity.setNumHijos(dto.getNumHijos());
        entity.setDescuentoBanco(dto.getDescuentoBanco());
        entity.setDescuentoInstitucion(dto.getDescuentoInstitucion());
        entity.setRegimenLaboralId(dto.getRegimenLaboralId());
        entity.setTipoContratoId(dto.getTipoContratoId());
        entity.setCondicionLaboralId(dto.getCondicionLaboralId());
        entity.setModalidadCasId(dto.getModalidadCasId());
        entity.setTipoPersonaMefId(dto.getTipoPersonaMefId());
        entity.setRegistroPlazaAirhsp(dto.getRegistroPlazaAirhsp());
        entity.setFechaInicioContrato(dto.getFechaInicioContrato());
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
                    dto.setCodigoAirhsp(e.getCodigoAirhsp());
                    dto.setMontoContrato(e.getMontoContrato());
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
                    dto.setModalidadCasId(e.getModalidadCasId());
                    dto.setTipoPersonaMefId(e.getTipoPersonaMefId());
                    dto.setRegistroPlazaAirhsp(e.getRegistroPlazaAirhsp());
                    dto.setFechaInicioContrato(e.getFechaInicioContrato());

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
                    if (e.getModalidadCasId() != null) {
                        modalidadCasRepository.findById(e.getModalidadCasId())
                                .ifPresent(mc -> dto.setModalidadCas(mc.getNombre()));
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

        RemuneracionCalculada remuneracion = calcularRemuneracionDesdeDto(dto);
        aplicarRemuneracion(entity, remuneracion);
        entity.setTieneAsignacionFamiliar(dto.getTieneAsignacionFamiliar());
        entity.setNumHijos(dto.getNumHijos());
        entity.setDescuentoBanco(dto.getDescuentoBanco());
        entity.setDescuentoInstitucion(dto.getDescuentoInstitucion());
        entity.setRegimenLaboralId(dto.getRegimenLaboralId());
        entity.setTipoContratoId(dto.getTipoContratoId());
        entity.setCondicionLaboralId(dto.getCondicionLaboralId());
        entity.setModalidadCasId(dto.getModalidadCasId());
        entity.setTipoPersonaMefId(dto.getTipoPersonaMefId());
        entity.setRegistroPlazaAirhsp(dto.getRegistroPlazaAirhsp());
        entity.setFechaInicioContrato(dto.getFechaInicioContrato());

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

    private RemuneracionCalculada calcularRemuneracionDesdeDto(EmpleadoPlanillaDto dto) {
        validarCodigoAirhsp(dto.getCodigoAirhsp());
        validarMontoContrato(dto.getMontoContrato());

        IncrementosDsResponseDto calculo = incrementosDsCalculoService.calcular(
                dto.getRegimenLaboralId(),
                dto.getCondicionLaboralId(),
                BigDecimal.valueOf(dto.getMontoContrato()),
                LocalDate.now());

        validarCoherenciaSueldoBasico(dto.getSueldoBasico(), calculo.remuneracionMensual());

        return new RemuneracionCalculada(
                dto.getCodigoAirhsp(),
                dto.getMontoContrato(),
                calculo.remuneracionMensual().doubleValue());
    }

    private static void validarCodigoAirhsp(String codigoAirhsp) {
        if (codigoAirhsp == null || !AIRHSP_PATTERN.matcher(codigoAirhsp).matches()) {
            throw new NegocioException("Código AIRHSP inválido: debe tener 6 dígitos numéricos");
        }
    }

    private static void validarMontoContrato(Double montoContrato) {
        if (montoContrato == null || montoContrato <= 0) {
            throw new NegocioException("Monto contratado inválido");
        }
    }

    private static void validarCoherenciaSueldoBasico(Double sueldoEnviado, BigDecimal sueldoCalculado) {
        if (sueldoEnviado == null) {
            return;
        }
        BigDecimal enviado = BigDecimal.valueOf(sueldoEnviado).setScale(2, RoundingMode.HALF_UP);
        if (enviado.subtract(sueldoCalculado).abs().compareTo(TOLERANCIA_SUELDO) > 0) {
            throw new NegocioException(
                    "Remuneración mensual no coincide con el cálculo del servidor");
        }
    }

    private static void aplicarRemuneracion(EmpleadoPlanilla entity, RemuneracionCalculada remuneracion) {
        entity.setCodigoAirhsp(remuneracion.codigoAirhsp());
        entity.setMontoContrato(remuneracion.montoContrato());
        entity.setSueldoBasico(remuneracion.sueldoBasico());
        entity.setMovilidad(null);
        entity.setAlimentacion(null);
    }

    private record RemuneracionCalculada(String codigoAirhsp, Double montoContrato, Double sueldoBasico) {
    }
}