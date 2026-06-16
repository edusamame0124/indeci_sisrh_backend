package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.previsional.*;
import com.indeci.rrhh.entity.AfpParametroVigencia;
import com.indeci.rrhh.entity.IndAfp;
import com.indeci.rrhh.entity.OnpParametroVigencia;
import com.indeci.rrhh.entity.PrevisionalLog;
import com.indeci.rrhh.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Gestión de parámetros previsionales AFP/ONP por vigencia.
 * REGLA-02: ningún valor porcentual hardcodeado — todo viene de BD.
 */
@Service
@RequiredArgsConstructor
public class ParametroPrevisionalService {

    private final IndAfpRepository              afpRepo;
    private final AfpParametroVigenciaRepository afpVigenciaRepo;
    private final OnpParametroVigenciaRepository onpVigenciaRepo;
    private final PrevisionalLogRepository      logRepo;
    private final EmpleadoPensionRepository     pensionRepo;

    // ── Catálogo AFP ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AfpCatalogoDto> listarAfpCatalogo() {
        return afpRepo.findByActivoOrderByNombreAsc(1).stream()
                .map(this::toAfpCatalogoDto)
                .toList();
    }

    // ── Parámetros AFP ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AfpParametroDto> listarAfpParametros(String estado) {
        List<AfpParametroVigencia> rows = (estado == null || estado.isBlank())
                ? afpVigenciaRepo.findAllWithAfp()
                : afpVigenciaRepo.findByEstado(estado);
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
            throw new IllegalStateException("Parámetro bloqueado por planilla cerrada.");
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
    public AfpParametroDto duplicarAfpVigencia(Long id, String usuario) {
        AfpParametroVigencia orig = afpVigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia AFP no encontrada: " + id));

        AfpParametroVigencia dup = new AfpParametroVigencia();
        dup.setAfp(orig.getAfp());
        dup.setPeriodoInicio(orig.getPeriodoInicio());
        dup.setPeriodoFin(orig.getPeriodoFin());
        dup.setAporteObligatorioPct(orig.getAporteObligatorioPct());
        dup.setComisionFlujoPct(orig.getComisionFlujoPct());
        dup.setComisionSaldoAnualPct(orig.getComisionSaldoAnualPct());
        dup.setPrimaSeguroPct(orig.getPrimaSeguroPct());
        dup.setRemuneracionMaximaAseg(orig.getRemuneracionMaximaAseg());
        dup.setFuenteOficial(orig.getFuenteOficial());
        dup.setUrlFuenteOficial(orig.getUrlFuenteOficial());
        dup.setFechaPublicacion(orig.getFechaPublicacion());
        dup.setObservacion("Duplicado de ID " + orig.getId());
        dup.setEstado("PROGRAMADO");
        dup.setBloqueadoPorPlanilla(0);
        dup.setCreadoPor(usuario);
        dup.setCreadoEn(LocalDateTime.now());
        afpVigenciaRepo.save(dup);

        registrarLog("AFP", orig.getAfp().getId(), orig.getAfp().getNombre(), "DUPLICAR",
                "Duplicado de vigencia ID " + orig.getId(), orig.getPeriodoInicio(), usuario);
        return toAfpDto(dup);
    }

    // ── Parámetros ONP ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OnpParametroDto> listarOnpParametros() {
        return onpVigenciaRepo.findAllByOrderByPeriodoInicioDesc().stream()
                .map(this::toOnpDto)
                .toList();
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
            throw new IllegalStateException("Parámetro bloqueado por planilla cerrada.");
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

    // ── KPI ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PrevisionalKpiDto kpi() {
        PrevisionalKpiDto dto = new PrevisionalKpiDto();
        dto.setAfpVigentes(afpVigenciaRepo.findByEstado("VIGENTE").size());
        dto.setOnpVigente(onpVigenciaRepo.findAllByOrderByPeriodoInicioDesc().stream()
                .filter(v -> "VIGENTE".equals(v.getEstado())).count());
        dto.setProximaVigencia(afpVigenciaRepo.findByEstado("PROGRAMADO").size()
                + onpVigenciaRepo.findAllByOrderByPeriodoInicioDesc().stream()
                        .filter(v -> "PROGRAMADO".equals(v.getEstado())).count());
        dto.setUltimaActualizacionSbs(resolverUltimaActualizacion());
        return dto;
    }

    // ── Resolver ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PrevisionalResolverResultDto resolver(Long empleadoId, String periodo) {
        var pension = pensionRepo.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .orElse(null);

        PrevisionalResolverResultDto dto = new PrevisionalResolverResultDto();
        if (pension == null) {
            dto.setEncontrado(false);
            dto.setMensaje("El empleado no tiene régimen pensionario activo registrado.");
            return dto;
        }

        String tipo = pension.getTipoRegimen();
        dto.setEncontrado(true);
        dto.setSistemaPensionario(tipo);

        if ("ONP".equalsIgnoreCase(tipo)) {
            Optional<OnpParametroVigencia> onp = onpVigenciaRepo.findVigenteByPeriodo(periodo);
            if (onp.isPresent()) {
                OnpParametroVigencia v = onp.get();
                dto.setAporteOnpPct(v.getAporteOnpPct());
                dto.setVigenciaInicio(v.getPeriodoInicio());
                dto.setVigenciaFin(v.getPeriodoFin());
                dto.setFuente(v.getFuenteOficial());
            } else {
                dto.setMensaje("No se encontró parámetro ONP vigente para el período " + periodo);
            }
        } else if ("AFP".equalsIgnoreCase(tipo)) {
            Long afpId = pension.getRegimenPensionarioId();
            Optional<AfpParametroVigencia> afp = afpVigenciaRepo.findVigenteByAfpAndPeriodo(afpId, periodo);
            if (afp.isPresent()) {
                AfpParametroVigencia v = afp.get();
                dto.setAfpNombre(v.getAfp().getNombre());
                dto.setTipoComision("FLUJO");
                dto.setAporteObligatorioPct(v.getAporteObligatorioPct());
                dto.setComisionFlujoPct(v.getComisionFlujoPct());
                dto.setComisionSaldoAnualPct(v.getComisionSaldoAnualPct());
                dto.setPrimaSeguroPct(v.getPrimaSeguroPct());
                dto.setRemuneracionMaximaAsegurable(v.getRemuneracionMaximaAseg());
                dto.setVigenciaInicio(v.getPeriodoInicio());
                dto.setVigenciaFin(v.getPeriodoFin());
                dto.setFuente(v.getFuenteOficial());
            } else {
                dto.setMensaje("No se encontró parámetro AFP vigente para el período " + periodo + " y AFP ID " + afpId);
            }
        }
        return dto;
    }

    // ── Log / Historial ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PrevisionalLogDto> historial() {
        return logRepo.findAllByOrderByFechaDesc().stream().map(this::toLogDto).toList();
    }

    // ── Helpers ──────────────────────────────────────────────

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
        v.setRemuneracionMaximaAseg(input.getRemuneracionMaximaAseg());
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
        return logRepo.findAllByOrderByFechaDesc().stream()
                .filter(l -> "AFP".equals(l.getTipo()))
                .findFirst()
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
        dto.setRemuneracionMaximaAseg(v.getRemuneracionMaximaAseg());
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
