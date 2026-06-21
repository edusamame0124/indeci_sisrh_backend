package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.essalud.*;
import com.indeci.rrhh.entity.EssaludVigencia;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EssaludVigenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class EssaludVigenciaService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final EssaludVigenciaRepository vigenciaRepo;
    private final EmpleadoRepository        empleadoRepo;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepo;

    // ── Listar ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EssaludVigenciaDto> listar(String estado, boolean incluirAnulados) {
        List<EssaludVigencia> rows;
        if (estado != null && !estado.isBlank()) {
            rows = vigenciaRepo.findByEstadoOrderByInicioDesc(estado);
        } else {
            rows = incluirAnulados
                    ? vigenciaRepo.findAllByOrderByVigenciaInicioDesc()
                    : vigenciaRepo.findAllExcluyendoAnulados();
        }
        return rows.stream().map(this::toDto).toList();
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public EssaludVigenciaDto crear(EssaludVigenciaInputDto input, String usuario) {
        validarFechas(input.getVigenciaInicio(), input.getVigenciaFin());
        validarTasas(input);
        validarNoSolapamiento(input.getVigenciaInicio(), input.getVigenciaFin(), null);

        EssaludVigencia v = new EssaludVigencia();
        mapInput(input, v);
        v.setAnioVigencia(input.getVigenciaInicio().getYear());
        v.setEstado("VIGENTE");
        v.setBloqueadoPorPlanilla(0);
        v.setCreadoPor(usuario);
        v.setCreadoEn(LocalDateTime.now());
        vigenciaRepo.save(v);
        return toDto(v);
    }

    // ── Editar ────────────────────────────────────────────────────────────────

    @Transactional
    public EssaludVigenciaDto editar(Long id, EssaludVigenciaInputDto input, String usuario) {
        EssaludVigencia v = vigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia EsSalud no encontrada: " + id));
        if (v.getBloqueadoPorPlanilla() == 1)
            throw new NegocioException(
                "No se puede editar esta vigencia porque ya fue utilizada en planilla. " +
                "Cree una nueva vigencia para mantener la trazabilidad.");
        if ("ANULADO".equals(v.getEstado()))
            throw new NegocioException("No se puede editar una vigencia anulada.");

        validarFechas(input.getVigenciaInicio(), input.getVigenciaFin());
        validarTasas(input);
        validarNoSolapamiento(input.getVigenciaInicio(), input.getVigenciaFin(), id);

        mapInput(input, v);
        v.setAnioVigencia(input.getVigenciaInicio().getYear());
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());
        return toDto(v);
    }

    // ── Cerrar ────────────────────────────────────────────────────────────────

    @Transactional
    public void cerrar(Long id, String usuario) {
        EssaludVigencia v = vigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia EsSalud no encontrada: " + id));
        if (v.getBloqueadoPorPlanilla() == 1)
            throw new NegocioException("Parámetro bloqueado por planilla cerrada.");
        v.setEstado("CERRADO");
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());
    }

    // ── Duplicar ──────────────────────────────────────────────────────────────

    @Transactional
    public EssaludVigenciaDto duplicar(Long id, EssaludDuplicarInputDto req, String usuario) {
        EssaludVigencia orig = vigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia EsSalud no encontrada: " + id));
        if ("ANULADO".equals(orig.getEstado()))
            throw new NegocioException("No se puede duplicar una vigencia anulada.");

        validarFechas(req.getVigenciaInicio(), req.getVigenciaFin());
        if (req.getVigenciaInicio().isBefore(orig.getVigenciaInicio()) ||
            req.getVigenciaInicio().isEqual(orig.getVigenciaInicio()))
            throw new NegocioException(
                "La fecha de inicio de la nueva vigencia debe ser posterior a la vigencia origen.");
        validarNoSolapamiento(req.getVigenciaInicio(), req.getVigenciaFin(), null);

        EssaludVigencia dup = new EssaludVigencia();
        dup.setAnioVigencia(req.getVigenciaInicio().getYear());
        dup.setVigenciaInicio(req.getVigenciaInicio());
        dup.setVigenciaFin(req.getVigenciaFin());
        dup.setUitVigente(req.getUitVigente());
        dup.setPctBaseCas(orig.getPctBaseCas());
        dup.setPctEssalud(orig.getPctEssalud());
        dup.setPctEssaludEps(orig.getPctEssaludEps());
        dup.setPctCreditoEps(orig.getPctCreditoEps());
        dup.setFuenteOficial(req.getFuenteOficial());
        dup.setUrlFuenteOficial(orig.getUrlFuenteOficial());
        dup.setFechaPublicacion(orig.getFechaPublicacion());
        dup.setObservacion(req.getObservacion() != null ? req.getObservacion()
                : "[Duplicado de vigencia " + orig.getAnioVigencia() + "]");
        dup.setEstado("PROGRAMADO");
        dup.setBloqueadoPorPlanilla(0);
        dup.setCreadoPor(usuario);
        dup.setCreadoEn(LocalDateTime.now());
        vigenciaRepo.save(dup);
        return toDto(dup);
    }

    // ── Anular ────────────────────────────────────────────────────────────────

    @Transactional
    public void anular(Long id, EssaludAnularInputDto req, String usuario) {
        EssaludVigencia v = vigenciaRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vigencia EsSalud no encontrada: " + id));
        if ("ANULADO".equals(v.getEstado()))
            throw new NegocioException("La vigencia ya está anulada.");
        if (v.getBloqueadoPorPlanilla() == 1)
            throw new NegocioException(
                "No se puede eliminar esta vigencia porque ya fue utilizada en planilla. " +
                "Para mantener la trazabilidad, el registro permanecerá protegido.");
        v.setEstado("ANULADO");
        v.setMotivoAnulacion(req.getMotivo());
        v.setAnuladoPor(usuario);
        v.setAnuladoEn(LocalDateTime.now());
        v.setModificadoPor(usuario);
        v.setModificadoEn(LocalDateTime.now());
    }

    // ── Resolver (validador por empleado + periodo) ────────────────────────────

    @Transactional(readOnly = true)
    public EssaludResolverResultDto resolver(Long empleadoId, String periodo, Boolean tieneEps) {
        EssaludResolverResultDto dto = new EssaludResolverResultDto();
        dto.setEmpleadoId(empleadoId);
        dto.setPeriodoConsultado(periodo);

        // Nombre y DNI del empleado
        List<Object[]> personaRows = empleadoRepo.findPersonaResumenByEmpleadoIds(List.of(empleadoId));
        if (!personaRows.isEmpty()) {
            Object[] row = personaRows.get(0);
            dto.setEmpleadoNombre(row[1] != null ? row[1].toString() : null);
            dto.setDocumento(row[2] != null ? row[2].toString() : null);
        }

        // Sueldo del empleado
        var planilla = empleadoPlanillaRepo.findFirstByEmpleadoIdAndActivo(
                empleadoId, 1).orElse(null);
        if (planilla == null || planilla.getSueldoBasico() == null) {
            dto.setEncontrado(false);
            dto.setEstadoValidacion("CONFIG_INCOMPLETA");
            dto.setMensaje("El empleado no tiene remuneración CAS registrada en el sistema.");
            return dto;
        }
        BigDecimal remuneracion = BigDecimal.valueOf(planilla.getSueldoBasico());
        dto.setRemuneracionCas(remuneracion);
        dto.setRegimenLaboral("CAS");

        // EPS
        boolean eps = tieneEps != null ? tieneEps : false;
        dto.setTieneEps(eps);

        // Vigencia aplicable para el periodo
        LocalDate fechaPeriodo = parsePeriodoToDate(periodo);
        if (fechaPeriodo == null) {
            dto.setEncontrado(false);
            dto.setEstadoValidacion("CONFIG_INCOMPLETA");
            dto.setMensaje("Formato de período inválido. Use YYYYMM o YYYY-MM.");
            return dto;
        }

        List<EssaludVigencia> vigencias = vigenciaRepo.findAplicableByFecha(fechaPeriodo);
        if (vigencias.isEmpty()) {
            dto.setEncontrado(true);
            dto.setEstadoValidacion("SIN_VIGENCIA");
            dto.setMensaje("No existe parámetro EsSalud/EPS vigente para el período " + periodo
                    + ". Registre una nueva vigencia antes de generar la planilla.");
            return dto;
        }

        EssaludVigencia v = vigencias.get(0);
        dto.setEncontrado(true);
        dto.setVigenciaId(v.getId());
        dto.setVigenciaInicio(v.getVigenciaInicio());
        dto.setVigenciaFin(v.getVigenciaFin());
        dto.setUitVigente(v.getUitVigente());
        dto.setPctBaseCas(v.getPctBaseCas());
        dto.setPctEssalud(v.getPctEssalud());
        dto.setPctEssaludEps(v.getPctEssaludEps());
        dto.setPctCreditoEps(v.getPctCreditoEps());
        dto.setFuenteOficial(v.getFuenteOficial());

        // Cálculo
        BigDecimal limiteUit = v.getUitVigente()
                .multiply(v.getPctBaseCas())
                .divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal base = remuneracion.compareTo(limiteUit) <= 0 ? remuneracion : limiteUit;
        dto.setLimiteUit(limiteUit);
        dto.setBaseAplicable(base);
        dto.setEssalud9(base.multiply(v.getPctEssalud()).divide(CIEN, 2, RoundingMode.HALF_UP));
        if (eps) {
            dto.setEssaludEps675(base.multiply(v.getPctEssaludEps()).divide(CIEN, 2, RoundingMode.HALF_UP));
            dto.setCreditoEps225(base.multiply(v.getPctCreditoEps()).divide(CIEN, 2, RoundingMode.HALF_UP));
        }
        dto.setEstadoValidacion("VALIDO");
        return dto;
    }

    // ── KPI ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long countVigentes() { return vigenciaRepo.countByEstado("VIGENTE"); }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void validarFechas(LocalDate ini, LocalDate fin) {
        if (fin != null && !fin.isAfter(ini))
            throw new NegocioException("La fecha de fin debe ser posterior a la fecha de inicio.");
    }

    private void validarTasas(EssaludVigenciaInputDto input) {
        BigDecimal sumaEps = input.getPctEssaludEps().add(input.getPctCreditoEps());
        if (sumaEps.compareTo(input.getPctEssalud()) != 0)
            throw new NegocioException(
                "La suma EsSalud con EPS (" + input.getPctEssaludEps()
                + "%) + Crédito EPS (" + input.getPctCreditoEps()
                + "%) debe ser igual a la tasa total EsSalud (" + input.getPctEssalud() + "%).");
    }

    private void validarNoSolapamiento(LocalDate ini, LocalDate fin, Long idExcluir) {
        long solapados = vigenciaRepo.countSolapamiento(ini, fin, idExcluir);
        if (solapados > 0)
            throw new NegocioException(
                "El rango de vigencia se solapa con otro parámetro EsSalud activo.");
    }

    private void mapInput(EssaludVigenciaInputDto input, EssaludVigencia v) {
        v.setVigenciaInicio(input.getVigenciaInicio());
        v.setVigenciaFin(input.getVigenciaFin());
        v.setUitVigente(input.getUitVigente());
        v.setPctBaseCas(input.getPctBaseCas());
        v.setPctEssalud(input.getPctEssalud());
        v.setPctEssaludEps(input.getPctEssaludEps());
        v.setPctCreditoEps(input.getPctCreditoEps());
        v.setFuenteOficial(input.getFuenteOficial());
        v.setUrlFuenteOficial(input.getUrlFuenteOficial());
        v.setFechaPublicacion(input.getFechaPublicacion());
        v.setObservacion(input.getObservacion());
    }

    private LocalDate parsePeriodoToDate(String periodo) {
        if (periodo == null) return null;
        String p = periodo.replace("-", "");
        if (p.length() != 6) return null;
        try {
            int anio = Integer.parseInt(p.substring(0, 4));
            int mes  = Integer.parseInt(p.substring(4, 6));
            return LocalDate.of(anio, mes, 1);
        } catch (Exception e) { return null; }
    }

    private EssaludVigenciaDto toDto(EssaludVigencia v) {
        EssaludVigenciaDto dto = new EssaludVigenciaDto();
        dto.setId(v.getId());
        dto.setAnioVigencia(v.getAnioVigencia());
        dto.setVigenciaInicio(v.getVigenciaInicio());
        dto.setVigenciaFin(v.getVigenciaFin());
        dto.setUitVigente(v.getUitVigente());
        dto.setPctBaseCas(v.getPctBaseCas());
        dto.setPctEssalud(v.getPctEssalud());
        dto.setPctEssaludEps(v.getPctEssaludEps());
        dto.setPctCreditoEps(v.getPctCreditoEps());
        dto.setFuenteOficial(v.getFuenteOficial());
        dto.setUrlFuenteOficial(v.getUrlFuenteOficial());
        dto.setFechaPublicacion(v.getFechaPublicacion());
        dto.setObservacion(v.getObservacion());
        dto.setEstado(v.getEstado());
        dto.setBloqueadoPorPlanilla(v.getBloqueadoPorPlanilla() == 1);
        dto.setCreadoPor(v.getCreadoPor());
        dto.setCreadoEn(v.getCreadoEn());
        // Campos calculados
        if (v.getUitVigente() != null && v.getPctBaseCas() != null) {
            BigDecimal base = v.getUitVigente().multiply(v.getPctBaseCas())
                    .divide(CIEN, 2, RoundingMode.HALF_UP);
            dto.setBaseMaximaCas(base);
            if (v.getPctEssalud() != null)
                dto.setEssaludMaximoCas(base.multiply(v.getPctEssalud())
                        .divide(CIEN, 2, RoundingMode.HALF_UP));
            if (v.getPctEssaludEps() != null)
                dto.setEssaludConEpsMax(base.multiply(v.getPctEssaludEps())
                        .divide(CIEN, 2, RoundingMode.HALF_UP));
            if (v.getPctCreditoEps() != null)
                dto.setCreditoEpsMax(base.multiply(v.getPctCreditoEps())
                        .divide(CIEN, 2, RoundingMode.HALF_UP));
        }
        return dto;
    }
}
