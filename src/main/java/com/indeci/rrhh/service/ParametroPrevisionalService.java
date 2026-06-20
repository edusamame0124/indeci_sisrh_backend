package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.previsional.*;
import com.indeci.rrhh.entity.AfpParametroVigencia;
import com.indeci.rrhh.entity.IndAfp;
import com.indeci.rrhh.entity.OnpParametroVigencia;
import com.indeci.rrhh.entity.PrevisionalLog;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.entity.TipoComisionAfp;
import com.indeci.rrhh.repository.*;
import com.indeci.exception.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Gestión de parámetros previsionales AFP/ONP por vigencia.
 * REGLA-02: ningún valor porcentual hardcodeado — todo viene de BD.
 */
@Service
@RequiredArgsConstructor
public class ParametroPrevisionalService {

    private static final Pattern PERIODO_CON_GUION = Pattern.compile("^\\d{4}-\\d{2}$");

    private final IndAfpRepository               afpRepo;
    private final AfpParametroVigenciaRepository  afpVigenciaRepo;
    private final OnpParametroVigenciaRepository  onpVigenciaRepo;
    private final PrevisionalLogRepository        logRepo;
    private final EmpleadoPensionRepository       pensionRepo;
    private final TipoComisionAfpRepository       tipoComisionAfpRepo;
    private final PeriodoPlanillaRepository       periodoPlanillaRepo;
    private final MovimientoPlanillaRepository    movimientoPlanillaRepo;
    private final EmpleadoRepository              empleadoRepo;
    private final RegimenPensionarioRepository    regimenPensionarioRepo;

    // ── Catálogo AFP ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AfpCatalogoDto> listarAfpCatalogo() {
        return afpRepo.findByActivoOrderByNombreAsc(1).stream()
                .map(this::toAfpCatalogoDto)
                .toList();
    }

    // ── Parámetros AFP ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AfpParametroDto> listarAfpParametros(String estado, boolean incluirAnulados) {
        List<AfpParametroVigencia> rows = (estado == null || estado.isBlank())
                ? (incluirAnulados
                        ? afpVigenciaRepo.findAllWithAfpIncluirAnulados()
                        : afpVigenciaRepo.findAllWithAfp())
                : afpVigenciaRepo.findByEstado(estado, incluirAnulados);
        return rows.stream().map(this::toAfpDto).toList();
    }

    @Transactional
    public AfpParametroDto crearAfpParametro(AfpParametroInputDto input, String usuario) {
        IndAfp afp = afpRepo.findById(input.getAfpId())
                .orElseThrow(() -> new NoSuchElementException("AFP no encontrada: " + input.getAfpId()));
        validarNoSolapamientoAfp(input.getAfpId(), input.getPeriodoInicio(), input.getPeriodoFin(), null);

        AfpParametroVigencia v = new AfpParametroVigencia();
        v.setAfp(afp);
        mapInputToAfp(input, v);
        v.setEstado("VIGENTE");
        v.setBloqueadoPorPlanilla(0);
        v.setCreadoPor(usuario);
        v.setCreadoEn(LocalDateTime.now());
        afpVigenciaRepo.save(v);

        registrarLog("AFP", afp.getId(), afp.getNombre(), "CREAR",
                "Nueva vigencia AFP " + afp.getNombre() + " desde " + input.getPeriodoInicio(),
                input.getPeriodoInicio(), usuario);
        return toAfpDto(v);
    }

    @Transactional
    public AfpParametroDto editarAfpParametro(Long id, AfpParametroInputDto input, String usuario) {
        AfpParametroVigencia v = afpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia AFP no encontrada: " + id));
        if (v.getBloqueadoPorPlanilla() == 1)
            throw new IllegalStateException(
                "No se puede editar este parámetro porque ya fue utilizado en una planilla cerrada. " +
                "Cree una nueva vigencia o duplique la vigente para mantener la trazabilidad.");
        validarNoSolapamientoAfp(v.getAfp().getId(), input.getPeriodoInicio(), input.getPeriodoFin(), id);

        mapInputToAfp(input, v);
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());

        registrarLog("AFP", v.getAfp().getId(), v.getAfp().getNombre(), "EDITAR",
                "Edición vigencia AFP " + v.getAfp().getNombre(), v.getPeriodoInicio(), usuario);
        return toAfpDto(v);
    }

    @Transactional
    public void cerrarAfpVigencia(Long id, String usuario) {
        AfpParametroVigencia v = afpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia AFP no encontrada: " + id));
        if (v.getBloqueadoPorPlanilla() == 1)
            throw new IllegalStateException("Parámetro bloqueado por planilla cerrada.");
        v.setEstado("CERRADO");
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());

        registrarLog("AFP", v.getAfp().getId(), v.getAfp().getNombre(), "CERRAR",
                "Cierre vigencia AFP " + v.getAfp().getNombre(), v.getPeriodoInicio(), usuario);
    }

    @Transactional
    public AfpParametroDto duplicarAfpVigencia(Long id, DuplicarVigenciaRequestDto req, String usuario) {
        AfpParametroVigencia orig = afpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia AFP no encontrada: " + id));

        validarOrigenDuplicacion(orig.getEstado(), orig.getFuenteOficial(), orig.getId());
        validarPeriodoPosterior(req.getPeriodoInicio(), orig.getPeriodoInicio());
        validarNoSolapamientoAfp(orig.getAfp().getId(), req.getPeriodoInicio(), null, null);

        AfpParametroVigencia dup = copiarAfpDesdeOrigen(orig, req, usuario);
        afpVigenciaRepo.save(dup);

        registrarLog("AFP", orig.getAfp().getId(), orig.getAfp().getNombre(), "DUPLICAR",
                "Duplicado de vigencia ID " + orig.getId() + " (período " + orig.getPeriodoInicio()
                + ") → nueva vigencia período " + req.getPeriodoInicio()
                + ". Fuente: " + req.getFuenteOficial(),
                req.getPeriodoInicio(), usuario);
        return toAfpDto(dup);
    }

    @Transactional
    public OnpParametroDto duplicarOnpVigencia(Long id, DuplicarVigenciaRequestDto req, String usuario) {
        OnpParametroVigencia orig = onpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia ONP no encontrada: " + id));

        validarOrigenDuplicacion(orig.getEstado(), orig.getFuenteOficial(), orig.getId());
        validarPeriodoPosterior(req.getPeriodoInicio(), orig.getPeriodoInicio());
        validarNoSolapamientoOnp(req.getPeriodoInicio(), null, null);

        OnpParametroVigencia dup = copiarOnpDesdeOrigen(orig, req, usuario);
        onpVigenciaRepo.save(dup);

        registrarLog("ONP", null, null, "DUPLICAR",
                "Duplicado de vigencia ONP ID " + orig.getId() + " (período " + orig.getPeriodoInicio()
                + ") → nueva vigencia período " + req.getPeriodoInicio()
                + ". Fuente: " + req.getFuenteOficial(),
                req.getPeriodoInicio(), usuario);
        return toOnpDto(dup);
    }

    // ── Parámetros ONP ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OnpParametroDto> listarOnpParametros(String estado, boolean incluirAnulados) {
        List<OnpParametroVigencia> rows = (estado == null || estado.isBlank())
                ? (incluirAnulados
                        ? onpVigenciaRepo.findAllByOrderByPeriodoInicioDesc()
                        : onpVigenciaRepo.findAllExcluyendoAnulados())
                : onpVigenciaRepo.findByEstado(estado, incluirAnulados);
        return rows.stream().map(this::toOnpDto).toList();
    }

    @Transactional
    public OnpParametroDto crearOnpParametro(OnpParametroInputDto input, String usuario) {
        validarNoSolapamientoOnp(input.getPeriodoInicio(), input.getPeriodoFin(), null);

        OnpParametroVigencia v = new OnpParametroVigencia();
        mapInputToOnp(input, v);
        v.setEstado("VIGENTE");
        v.setBloqueadoPorPlanilla(0);
        v.setCreadoPor(usuario);
        v.setCreadoEn(LocalDateTime.now());
        onpVigenciaRepo.save(v);

        registrarLog("ONP", null, null, "CREAR",
                "Nueva vigencia ONP desde " + input.getPeriodoInicio(), input.getPeriodoInicio(), usuario);
        return toOnpDto(v);
    }

    @Transactional
    public OnpParametroDto editarOnpParametro(Long id, OnpParametroInputDto input, String usuario) {
        OnpParametroVigencia v = onpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia ONP no encontrada: " + id));
        if (v.getBloqueadoPorPlanilla() == 1)
            throw new IllegalStateException(
                "No se puede editar este parámetro porque ya fue utilizado en una planilla cerrada. " +
                "Cree una nueva vigencia o duplique la vigente para mantener la trazabilidad.");
        validarNoSolapamientoOnp(input.getPeriodoInicio(), input.getPeriodoFin(), id);

        mapInputToOnp(input, v);
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());

        registrarLog("ONP", null, null, "EDITAR",
                "Edición vigencia ONP", v.getPeriodoInicio(), usuario);
        return toOnpDto(v);
    }

    @Transactional
    public void cerrarOnpVigencia(Long id, String usuario) {
        OnpParametroVigencia v = onpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia ONP no encontrada: " + id));
        if (v.getBloqueadoPorPlanilla() == 1)
            throw new IllegalStateException("Parámetro bloqueado por planilla cerrada.");
        v.setEstado("CERRADO");
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());

        registrarLog("ONP", null, null, "CERRAR",
                "Cierre vigencia ONP", v.getPeriodoInicio(), usuario);
    }

    // ── Anulación lógica (Eliminar en UI = ANULADO en BD) ───

    @Transactional
    public void anularAfpVigencia(Long id, String motivo, String usuario) {
        AfpParametroVigencia v = afpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia AFP no encontrada: " + id));

        validarPuedeAnular(v.getEstado(), v.getBloqueadoPorPlanilla(), id, "AFP");
        validarNoUsadaEnPlanilla(v.getPeriodoInicio(), v.getPeriodoFin(), v.getId(), null, "AFP");

        v.setEstado("ANULADO");
        v.setMotivoAnulacion(motivo);
        v.setAnuladoPor(usuario);
        v.setAnuladoEn(LocalDateTime.now());
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());

        registrarLog("AFP", v.getAfp().getId(), v.getAfp().getNombre(), "ANULAR",
                "Anulación lógica vigencia AFP " + v.getAfp().getNombre()
                + " (período " + v.getPeriodoInicio() + "). Motivo: " + motivo,
                v.getPeriodoInicio(), usuario);
    }

    @Transactional
    public void anularOnpVigencia(Long id, String motivo, String usuario) {
        OnpParametroVigencia v = onpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia ONP no encontrada: " + id));

        validarPuedeAnular(v.getEstado(), v.getBloqueadoPorPlanilla(), id, "ONP");
        validarNoUsadaEnPlanilla(v.getPeriodoInicio(), v.getPeriodoFin(), null, v.getId(), "ONP");

        v.setEstado("ANULADO");
        v.setMotivoAnulacion(motivo);
        v.setAnuladoPor(usuario);
        v.setAnuladoEn(LocalDateTime.now());
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());

        registrarLog("ONP", null, null, "ANULAR",
                "Anulación lógica vigencia ONP"
                + " (período " + v.getPeriodoInicio() + "). Motivo: " + motivo,
                v.getPeriodoInicio(), usuario);
    }

    // ── KPI ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PrevisionalKpiDto kpi() {
        PrevisionalKpiDto dto = new PrevisionalKpiDto();
        dto.setAfpVigentes((int) afpVigenciaRepo.countByEstado("VIGENTE"));
        dto.setOnpVigente(onpVigenciaRepo.countByEstado("VIGENTE"));
        dto.setProximaVigencia(afpVigenciaRepo.countByEstado("PROGRAMADO")
                + onpVigenciaRepo.countByEstado("PROGRAMADO"));
        dto.setUltimaActualizacionSbs(resolverUltimaActualizacion());
        return dto;
    }

    // ── Resolver ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PrevisionalResolverResultDto resolver(Long empleadoId, String periodo) {
        String periodoNorm = normalizarPeriodo(periodo);

        PrevisionalResolverResultDto dto = new PrevisionalResolverResultDto();
        dto.setEmpleadoId(empleadoId);
        dto.setPeriodoConsultado(periodoNorm);

        List<Object[]> personaRows = empleadoRepo.findPersonaResumenByEmpleadoIds(List.of(empleadoId));
        if (!personaRows.isEmpty()) {
            Object[] row = personaRows.get(0);
            dto.setEmpleadoNombre(row[1] != null ? row[1].toString() : null);
            dto.setDocumento(row[2] != null ? row[2].toString() : null);
        }

        var pension = pensionRepo.findFirstByEmpleadoIdAndActivo(empleadoId, 1).orElse(null);
        if (pension == null) {
            dto.setEncontrado(false);
            dto.setEstadoValidacion("CONFIG_INCOMPLETA");
            dto.setMensaje("El empleado no tiene régimen pensionario activo registrado.");
            return dto;
        }

        String tipo = pension.getTipoRegimen();
        // Inferir tipo desde regimenPensionarioId cuando TIPO_REGIMEN no está populado en BD
        if (tipo == null || tipo.isBlank()) {
            tipo = (pension.getRegimenPensionarioId() != null) ? "AFP" : null;
        }
        if (tipo == null) {
            dto.setEncontrado(false);
            dto.setEstadoValidacion("CONFIG_INCOMPLETA");
            dto.setMensaje("El empleado tiene un registro pensionario activo pero sin régimen definido "
                    + "(ONP o AFP). Corrija la ficha pensionaria del trabajador.");
            return dto;
        }
        dto.setEncontrado(true);
        dto.setSistemaPensionario(tipo);

        if ("ONP".equalsIgnoreCase(tipo)) {
            Optional<OnpParametroVigencia> onp = onpVigenciaRepo.findAplicableByPeriodo(periodoNorm);
            if (onp.isPresent()) {
                OnpParametroVigencia v = onp.get();
                dto.setVigenciaId(v.getId());
                dto.setAporteOnpPct(v.getAporteOnpPct());
                dto.setVigenciaInicio(v.getPeriodoInicio());
                dto.setVigenciaFin(v.getPeriodoFin());
                dto.setFuente(v.getFuenteOficial());
                dto.setEstadoValidacion("VALIDO");
            } else {
                dto.setEstadoValidacion("SIN_VIGENCIA");
                dto.setMensaje("No existe parámetro ONP vigente para el período " + periodoNorm
                        + ". Debe registrar una nueva vigencia antes de calcular la planilla.");
            }
        } else if ("AFP".equalsIgnoreCase(tipo)) {
            Long regimenId = pension.getRegimenPensionarioId();
            if (regimenId == null) {
                dto.setEstadoValidacion("CONFIG_INCOMPLETA");
                dto.setMensaje("El trabajador no tiene AFP configurada en su ficha pensionaria.");
                return dto;
            }
            // INDECI_REGIMEN_PENSIONARIO.ID ≠ INDECI_AFP.ID — bridge via CODIGO o NOMBRE
            RegimenPensionario regimen = regimenPensionarioRepo.findById(regimenId).orElse(null);
            if (regimen == null) {
                dto.setEstadoValidacion("CONFIG_INCOMPLETA");
                dto.setMensaje("No se pudo identificar la AFP del trabajador. Corrija la ficha pensionaria.");
                return dto;
            }
            IndAfp afpCatalog = resolverAfpCatalog(regimen);
            if (afpCatalog == null) {
                dto.setEstadoValidacion("CONFIG_INCOMPLETA");
                dto.setMensaje("La AFP '" + (regimen.getNombre() != null ? regimen.getNombre() : regimen.getCodigo())
                        + "' no existe en el catálogo AFP. Contacte al administrador.");
                return dto;
            }
            Long afpId = afpCatalog.getId();
            dto.setAfpId(afpId);
            Optional<AfpParametroVigencia> afp = afpVigenciaRepo.findAplicableByAfpAndPeriodo(afpId, periodoNorm);
            if (afp.isPresent()) {
                AfpParametroVigencia v = afp.get();
                dto.setVigenciaId(v.getId());
                dto.setAfpNombre(v.getAfp().getNombre());
                dto.setTipoComision(resolverTipoComision(pension.getTipoComisionAfpId()));
                dto.setAporteObligatorioPct(v.getAporteObligatorioPct());
                dto.setComisionFlujoPct(v.getComisionFlujoPct());
                dto.setComisionSaldoAnualPct(v.getComisionSaldoAnualPct());
                dto.setPrimaSeguroPct(v.getPrimaSeguroPct());
                dto.setRemuneracionMaximaAsegurable(v.getRemuneracionMaximaAseg());
                dto.setVigenciaInicio(v.getPeriodoInicio());
                dto.setVigenciaFin(v.getPeriodoFin());
                dto.setFuente(v.getFuenteOficial());
                dto.setEstadoValidacion("VALIDO");
            } else {
                dto.setEstadoValidacion("SIN_VIGENCIA");
                dto.setMensaje("No existe parámetro AFP vigente para el período " + periodoNorm
                        + ". Debe registrar una nueva vigencia antes de calcular la planilla.");
            }
        } else {
            dto.setEstadoValidacion("CONFIG_INCOMPLETA");
            dto.setMensaje("El régimen pensionario '" + tipo + "' no es reconocido. Contacte al administrador.");
        }
        return dto;
    }

    private static String normalizarPeriodo(String periodo) {
        if (periodo != null && PERIODO_CON_GUION.matcher(periodo).matches()) {
            return periodo.replace("-", "");
        }
        return periodo;
    }

    // ── Log / Historial ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PrevisionalLogDto> historial() {
        return logRepo.findAllByOrderByFechaDesc().stream().map(this::toLogDto).toList();
    }

    // ── Helpers ──────────────────────────────────────────────

    private void validarPuedeAnular(String estado, Integer bloqueado, Long id, String tipo) {
        if ("ANULADO".equals(estado))
            throw new NegocioException("La vigencia " + tipo + " ID " + id + " ya se encuentra anulada.");
        if (bloqueado != null && bloqueado == 1)
            throw new NegocioException(
                "No se puede eliminar esta vigencia porque ya fue utilizada en planilla. " +
                "Para mantener la trazabilidad, el registro permanecerá protegido.");
    }

    /**
     * Validación conservadora de uso en planilla.
     * Regla: si NO se puede confirmar que la vigencia NO fue usada → bloquear.
     *
     * Verifica en orden:
     *  1. Trazabilidad directa B2 (afpParamVigenciaId / onpParamVigenciaId en movimientos).
     *  2. Planillas CERRADAS/APROBADAS cuyo período cae dentro del rango de vigencia.
     *
     * Si no existe enlace directo pero sí planillas en el rango → bloquear (conservador).
     */
    private void validarNoUsadaEnPlanilla(String periodoInicio, String periodoFin,
                                          Long afpVigenciaId, Long onpVigenciaId, String tipo) {
        // 1. Enlace directo (B2 — movimientos con vigencia FK)
        if (afpVigenciaId != null) {
            List<Long> directos = movimientoPlanillaRepo.findDistinctAfpVigenciaIdsByPeriodo(periodoInicio);
            if (directos.contains(afpVigenciaId))
                throw new NegocioException(
                    "No se puede eliminar esta vigencia AFP porque ya fue utilizada en planilla. " +
                    "Para mantener la trazabilidad, el registro permanecerá protegido.");
        }
        if (onpVigenciaId != null) {
            List<Long> directos = movimientoPlanillaRepo.findDistinctOnpVigenciaIdsByPeriodo(periodoInicio);
            if (directos.contains(onpVigenciaId))
                throw new NegocioException(
                    "No se puede eliminar esta vigencia ONP porque ya fue utilizada en planilla. " +
                    "Para mantener la trazabilidad, el registro permanecerá protegido.");
        }

        // 2. Planillas cerradas/aprobadas en el rango de vigencia (validación por período)
        long planillasCerradas = periodoPlanillaRepo.countPlanillasCerradasEnRango(
                periodoInicio, periodoFin);
        if (planillasCerradas > 0)
            throw new NegocioException(
                "No se puede eliminar esta vigencia " + tipo + " porque hay "
                + planillasCerradas + " planilla(s) CERRADA(S)/APROBADA(S) en su rango de vigencia. " +
                "Para mantener la trazabilidad, el registro permanecerá protegido.");
    }

    /**
     * Resuelve IndAfp desde un RegimenPensionario con tres niveles de fallback:
     * 1. CODIGO exacto (filas bien sembradas: 'INTEGRA', 'PRIMA', etc.)
     * 2. NOMBRE exacto contra AFP.CODIGO (filas legacy con CODIGO = ID numérico)
     * 3. AFP.NOMBRE LIKE '%NOMBRE%' (último recurso)
     */
    private IndAfp resolverAfpCatalog(RegimenPensionario regimen) {
        // Nivel 1: CODIGO exacto
        if (regimen.getCodigo() != null && !regimen.getCodigo().isBlank()) {
            var r = afpRepo.findByCodigoIgnoreCase(regimen.getCodigo());
            if (r.isPresent()) return r.get();
        }
        // Nivel 2: NOMBRE del regimen contra CODIGO de IndAfp
        if (regimen.getNombre() != null && !regimen.getNombre().isBlank()) {
            var r = afpRepo.findByCodigoIgnoreCase(regimen.getNombre());
            if (r.isPresent()) return r.get();
        }
        // Nivel 3: NOMBRE del regimen contenido en AFP.NOMBRE (ej: "INTEGRA" en "AFP Integra")
        if (regimen.getNombre() != null && !regimen.getNombre().isBlank()) {
            return afpRepo.findFirstByNombreContainingIgnoreCase(regimen.getNombre()).orElse(null);
        }
        return null;
    }

    private String resolverTipoComision(Long tipoComisionAfpId) {
        if (tipoComisionAfpId == null) return "FLUJO";
        return tipoComisionAfpRepo.findById(tipoComisionAfpId)
                .map(TipoComisionAfp::getCodigo)
                .orElse("FLUJO");
    }

    private void validarOrigenDuplicacion(String estado, String fuenteOficial, Long origenId) {
        if ("INACTIVO".equals(estado))
            throw new IllegalStateException(
                "No se puede duplicar la vigencia ID " + origenId + ": estado INACTIVO (anulada).");
        if (fuenteOficial == null || fuenteOficial.isBlank())
            throw new IllegalStateException(
                "La vigencia ID " + origenId + " está incompleta: falta fuente oficial.");
    }

    private void validarPeriodoPosterior(String nuevo, String origen) {
        if (nuevo.compareTo(origen) <= 0)
            throw new IllegalArgumentException(
                "El período de inicio de la nueva vigencia (" + nuevo
                + ") debe ser posterior al período origen (" + origen + ").");
    }

    private AfpParametroVigencia copiarAfpDesdeOrigen(AfpParametroVigencia orig,
                                                      DuplicarVigenciaRequestDto req, String usuario) {
        AfpParametroVigencia dup = new AfpParametroVigencia();
        dup.setAfp(orig.getAfp());
        dup.setPeriodoInicio(req.getPeriodoInicio());
        dup.setPeriodoFin(null);
        dup.setAporteObligatorioPct(orig.getAporteObligatorioPct());
        dup.setComisionFlujoPct(orig.getComisionFlujoPct());
        dup.setComisionSaldoAnualPct(orig.getComisionSaldoAnualPct());
        dup.setPrimaSeguroPct(orig.getPrimaSeguroPct());
        dup.setRemuneracionMaximaAseg(orig.getRemuneracionMaximaAseg());
        dup.setFuenteOficial(req.getFuenteOficial());
        dup.setUrlFuenteOficial(orig.getUrlFuenteOficial());
        dup.setFechaPublicacion(orig.getFechaPublicacion());
        dup.setObservacion(req.getObservacion()
                + " [origen ID " + orig.getId() + " período " + orig.getPeriodoInicio() + "]");
        dup.setEstado("PROGRAMADO");
        dup.setBloqueadoPorPlanilla(0);
        dup.setCreadoPor(usuario);
        dup.setCreadoEn(LocalDateTime.now());
        return dup;
    }

    private OnpParametroVigencia copiarOnpDesdeOrigen(OnpParametroVigencia orig,
                                                      DuplicarVigenciaRequestDto req, String usuario) {
        OnpParametroVigencia dup = new OnpParametroVigencia();
        dup.setPeriodoInicio(req.getPeriodoInicio());
        dup.setPeriodoFin(null);
        dup.setAporteOnpPct(orig.getAporteOnpPct());
        dup.setFuenteOficial(req.getFuenteOficial());
        dup.setUrlFuenteOficial(orig.getUrlFuenteOficial());
        dup.setFechaPublicacion(orig.getFechaPublicacion());
        dup.setObservacion(req.getObservacion()
                + " [origen ID " + orig.getId() + " período " + orig.getPeriodoInicio() + "]");
        dup.setEstado("PROGRAMADO");
        dup.setBloqueadoPorPlanilla(0);
        dup.setCreadoPor(usuario);
        dup.setCreadoEn(LocalDateTime.now());
        return dup;
    }

    private void validarNoSolapamientoAfp(Long afpId, String ini, String fin, Long idExcluir) {
        long solapados = afpVigenciaRepo.countSolapamiento(afpId, ini, fin, idExcluir);
        if (solapados > 0)
            throw new IllegalArgumentException("El rango de vigencia se solapa con otro parámetro activo para esta AFP.");
    }

    private void validarNoSolapamientoOnp(String ini, String fin, Long idExcluir) {
        long solapados = onpVigenciaRepo.countSolapamiento(ini, fin, idExcluir);
        if (solapados > 0)
            throw new IllegalArgumentException("El rango de vigencia ONP se solapa con otro parámetro activo.");
    }

    private void mapInputToAfp(AfpParametroInputDto input, AfpParametroVigencia v) {
        v.setPeriodoInicio(input.getPeriodoInicio());
        v.setPeriodoFin(input.getPeriodoFin());
        v.setAporteObligatorioPct(input.getAporteObligatorioPct());
        v.setComisionFlujoPct(input.getComisionFlujoPct());
        v.setComisionSaldoAnualPct(input.getComisionSaldoAnualPct());
        v.setPrimaSeguroPct(input.getPrimaSeguroPct());
        v.setRemuneracionMaximaAseg(input.getRemuneracionMaximaAsegurable());
        v.setFuenteOficial(input.getFuenteOficial());
        v.setUrlFuenteOficial(input.getUrlFuenteOficial());
        v.setFechaPublicacion(input.getFechaPublicacion());
        v.setObservacion(input.getObservacion());
    }

    private void mapInputToOnp(OnpParametroInputDto input, OnpParametroVigencia v) {
        v.setPeriodoInicio(input.getPeriodoInicio());
        v.setPeriodoFin(input.getPeriodoFin());
        v.setAporteOnpPct(input.getAporteOnpPct());
        v.setFuenteOficial(input.getFuenteOficial());
        v.setUrlFuenteOficial(input.getUrlFuenteOficial());
        v.setFechaPublicacion(input.getFechaPublicacion());
        v.setObservacion(input.getObservacion());
    }

    private void registrarLog(String tipo, Long afpId, String afpNombre,
                              String accion, String desc, String periodo, String usuario) {
        PrevisionalLog log = new PrevisionalLog();
        log.setTipo(tipo);
        log.setAfpId(afpId);
        log.setAfpNombre(afpNombre);
        log.setAccion(accion);
        log.setDescripcion(desc);
        log.setPeriodoAfectado(periodo);
        log.setUsuario(usuario);
        log.setFecha(LocalDateTime.now());
        logRepo.save(log);
    }

    private String resolverUltimaActualizacion() {
        return logRepo.findFirstByTipoOrderByFechaDesc("AFP")
                .map(l -> l.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .orElse("—");
    }

    private AfpCatalogoDto toAfpCatalogoDto(IndAfp afp) {
        AfpCatalogoDto dto = new AfpCatalogoDto();
        dto.setId(afp.getId());
        dto.setCodigo(afp.getCodigo());
        dto.setNombre(afp.getNombre());
        dto.setActivo(afp.getActivo() == 1);
        return dto;
    }

    private AfpParametroDto toAfpDto(AfpParametroVigencia v) {
        AfpParametroDto dto = new AfpParametroDto();
        dto.setId(v.getId());
        dto.setAfpId(v.getAfp().getId());
        dto.setAfpNombre(v.getAfp().getNombre());
        dto.setPeriodoInicio(v.getPeriodoInicio());
        dto.setPeriodoFin(v.getPeriodoFin());
        dto.setAporteObligatorioPct(v.getAporteObligatorioPct());
        dto.setComisionFlujoPct(v.getComisionFlujoPct());
        dto.setComisionSaldoAnualPct(v.getComisionSaldoAnualPct());
        dto.setPrimaSeguroPct(v.getPrimaSeguroPct());
        dto.setRemuneracionMaximaAsegurable(v.getRemuneracionMaximaAseg());
        dto.setFuenteOficial(v.getFuenteOficial());
        dto.setUrlFuenteOficial(v.getUrlFuenteOficial());
        dto.setFechaPublicacion(v.getFechaPublicacion());
        dto.setObservacion(v.getObservacion());
        dto.setEstado(v.getEstado());
        dto.setBloqueadoPorPlanilla(v.getBloqueadoPorPlanilla() == 1);
        dto.setCreadoPor(v.getCreadoPor());
        dto.setCreadoEn(v.getCreadoEn());
        return dto;
    }

    private OnpParametroDto toOnpDto(OnpParametroVigencia v) {
        OnpParametroDto dto = new OnpParametroDto();
        dto.setId(v.getId());
        dto.setPeriodoInicio(v.getPeriodoInicio());
        dto.setPeriodoFin(v.getPeriodoFin());
        dto.setAporteOnpPct(v.getAporteOnpPct());
        dto.setFuenteOficial(v.getFuenteOficial());
        dto.setUrlFuenteOficial(v.getUrlFuenteOficial());
        dto.setFechaPublicacion(v.getFechaPublicacion());
        dto.setObservacion(v.getObservacion());
        dto.setEstado(v.getEstado());
        dto.setBloqueadoPorPlanilla(v.getBloqueadoPorPlanilla() == 1);
        dto.setCreadoPor(v.getCreadoPor());
        dto.setCreadoEn(v.getCreadoEn());
        return dto;
    }

    private PrevisionalLogDto toLogDto(PrevisionalLog l) {
        PrevisionalLogDto dto = new PrevisionalLogDto();
        dto.setId(l.getId());
        dto.setTipo(l.getTipo());
        dto.setAfpNombre(l.getAfpNombre());
        dto.setAccion(l.getAccion());
        dto.setDescripcion(l.getDescripcion());
        dto.setUsuario(l.getUsuario());
        dto.setFecha(l.getFecha());
        dto.setPeriodoAfectado(l.getPeriodoAfectado());
        return dto;
    }
}
