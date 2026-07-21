package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import org.springframework.transaction.annotation.Transactional;
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
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Motivo del cierre automático por solapamiento (transición Determinado→Indeterminado, etc.).
     * Es un cierre "suave": marca el fin del contrato anterior SIN documento de sustento, por lo
     * que NO habilita LBS ({@code VinculoEstadoResolver.habilitaLbs} exige documento). El módulo
     * de Liquidaciones distingue así una transición de un cese real. LEY-A (decisión RR.HH.).
     */
    public static final String MOTIVO_CESE_TRANSICION = "TRANSICIÓN DE CONTRATO";

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
    @Transactional
    public void guardar(EmpleadoPlanillaDto dto) {

        // Un empleado solo puede tener UN vínculo vigente a la vez (SERVIR/MEF). Vínculos
        // secuenciales (rotación CAS): al registrar un contrato nuevo, se CIERRA
        // automáticamente el/los anterior(es) vigente(s) poniéndoles fecha de cese = día
        // previo al inicio del nuevo. Es un cierre "suave": se registra motivo pero NO
        // documento, así el cese no habilita LBS por sí solo (VinculoEstadoResolver.habilitaLbs
        // exige ambos). El estado del vínculo se DERIVA de las fechas — no hay campo 'estado'
        // editable. Un vínculo cesado conserva activo=1 y queda en la historia; activo=0 = ANULADO.
        List<EmpleadoPlanilla> activos =
                repository.findByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        final LocalDate inicioNuevo = dto.getFechaInicioContrato();
        final LocalDate fechaCierre = inicioNuevo != null ? inicioNuevo.minusDays(1) : LocalDate.now();
        activos.stream()
                .filter(v -> v.getFechaCese() == null)
                .forEach(v -> {
                    v.setFechaCese(fechaCierre);
                    // Sello anti-liquidación: motivo explícito + SIN documento → no dispara LBS.
                    v.setMotivoCese(MOTIVO_CESE_TRANSICION);
                    v.setDocumentoCese(null);
                    v.setUpdatedAt(LocalDateTime.now());
                    repository.save(v);
                });

        // El nuevo contrato debe iniciar después de cualquier cese (incluye los que se acaban
        // de aplicar arriba: su cese = inicioNuevo - 1, así que esta guarda pasa; solo bloquea
        // si hubiera un cese histórico posterior al inicio del nuevo).
        Optional<LocalDate> ultimoCese = activos.stream()
                .map(EmpleadoPlanilla::getFechaCese)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo);
        if (ultimoCese.isPresent()
                && (inicioNuevo == null || !inicioNuevo.isAfter(ultimoCese.get()))) {
            throw new NegocioException(
                    "El nuevo contrato debe iniciar después del cese del contrato anterior ("
                            + ultimoCese.get().format(FMT_FECHA) + ").");
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
        // Gate de Teletrabajo (Ley N° 31572, V012_28): default 0 si no viene.
        entity.setEsTeletrabajador(
                Integer.valueOf(1).equals(dto.getEsTeletrabajador()) ? 1 : 0);
        entity.setGrupoServidorCivil(dto.getGrupoServidorCivil());
        entity.setEsConfianza(dto.getEsConfianza());
        entity.setTipoPersonaMefId(dto.getTipoPersonaMefId());
        entity.setRegistroPlazaAirhsp(dto.getRegistroPlazaAirhsp());
        entity.setFechaInicioContrato(dto.getFechaInicioContrato());
        entity.setDiasSemanaOperativo(dto.getDiasSemanaOperativo());
        aplicarFechasYCese(entity, dto);
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
                    dto.setEsTeletrabajador(e.getEsTeletrabajador());
                    dto.setGrupoServidorCivil(e.getGrupoServidorCivil());
                    dto.setEsConfianza(e.getEsConfianza());
                    dto.setTipoPersonaMefId(e.getTipoPersonaMefId());
                    dto.setRegistroPlazaAirhsp(e.getRegistroPlazaAirhsp());
                    dto.setFechaInicioContrato(e.getFechaInicioContrato());
                    dto.setDiasSemanaOperativo(e.getDiasSemanaOperativo());
                    dto.setFechaFin(e.getFechaFin());
                    dto.setFechaCese(e.getFechaCese());
                    dto.setMotivoCese(e.getMotivoCese());
                    dto.setDocumentoCese(e.getDocumentoCese());
                    dto.setDocumentoOrigenTipo(e.getDocumentoOrigenTipo());
                    dto.setDocumentoOrigenNumero(e.getDocumentoOrigenNumero());
                    dto.setDocumentoOrigenFecha(e.getDocumentoOrigenFecha());

                    // Estado del vínculo DERIVADO (no editable). Inicio = fecha del
                    // contrato con respaldo a FECHA_INICIO legacy.
                    java.time.LocalDate inicio = e.getFechaInicioContrato() != null
                            ? e.getFechaInicioContrato() : e.getFechaInicio();
                    var estado = com.indeci.rrhh.vinculacion.VinculoEstadoResolver.derivar(
                            e.getActivo(), inicio, e.getFechaFin(), e.getFechaCese(),
                            java.time.LocalDate.now());
                    dto.setEstadoVinculo(estado.name());
                    dto.setHabilitaLbs(com.indeci.rrhh.vinculacion.VinculoEstadoResolver.habilitaLbs(
                            estado, e.getFechaCese(), e.getMotivoCese(), e.getDocumentoCese()));
                    dto.setPlazoMaximo(e.getPlazoMaximo());

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
        // Gate de Teletrabajo (Ley N° 31572, V012_28): default 0 si no viene.
        entity.setEsTeletrabajador(
                Integer.valueOf(1).equals(dto.getEsTeletrabajador()) ? 1 : 0);
        entity.setGrupoServidorCivil(dto.getGrupoServidorCivil());
        entity.setEsConfianza(dto.getEsConfianza());
        entity.setTipoPersonaMefId(dto.getTipoPersonaMefId());
        entity.setRegistroPlazaAirhsp(dto.getRegistroPlazaAirhsp());
        entity.setFechaInicioContrato(dto.getFechaInicioContrato());
        entity.setDiasSemanaOperativo(dto.getDiasSemanaOperativo());
        aplicarFechasYCese(entity, dto);

        repository.save(entity);

        auditoriaContext.setDetalle("Planilla actualizada ID: " + id);
    }

    /**
     * Aplica fecha fin contractual y los hechos de cese al vínculo. Regla (RR.HH.):
     * si se registra una fecha de cese, el motivo y el documento de sustento son
     * obligatorios (el estado CESADO se deriva a partir de estos hechos).
     */
    private void aplicarFechasYCese(EmpleadoPlanilla entity, EmpleadoPlanillaDto dto) {
        // La fecha de término (fechaFin) se persiste tal como venga, sin depender del tipo de
        // contrato (decisión RR.HH.): el campo está siempre habilitado en el formulario.
        final LocalDate fechaFin = dto.getFechaFin();
        entity.setFechaFin(fechaFin);

        final LocalDate fechaCese = dto.getFechaCese();
        if (fechaCese != null) {
            // Cese formal (fáctico): exige motivo + documento — es lo que detona la LBS.
            if (dto.getMotivoCese() == null || dto.getMotivoCese().isBlank()
                    || dto.getDocumentoCese() == null || dto.getDocumentoCese().isBlank()) {
                throw new NegocioException(
                        "Para registrar el cese se requieren fecha de cese, motivo y documento de sustento");
            }
            // Regla 2.2: el cese no puede ser anterior al inicio del contrato.
            final LocalDate inicio = dto.getFechaInicioContrato();
            if (inicio != null && fechaCese.isBefore(inicio)) {
                throw new NegocioException(
                        "La fecha de cese no puede ser anterior al inicio del contrato ("
                                + inicio.format(FMT_FECHA) + ").");
            }
            // Regla 2.2: si el contrato tiene término, el cese no puede superarlo.
            if (fechaFin != null && fechaCese.isAfter(fechaFin)) {
                throw new NegocioException(
                        "La fecha de cese no puede ser posterior a la fecha de término del contrato ("
                                + fechaFin.format(FMT_FECHA) + ").");
            }
        }
        entity.setFechaCese(fechaCese);
        entity.setMotivoCese(dto.getMotivoCese());
        entity.setDocumentoCese(dto.getDocumentoCese());
        // Sustento de origen del vínculo (V012_08).
        entity.setDocumentoOrigenTipo(dto.getDocumentoOrigenTipo());
        entity.setDocumentoOrigenNumero(dto.getDocumentoOrigenNumero());
        entity.setDocumentoOrigenFecha(dto.getDocumentoOrigenFecha());
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