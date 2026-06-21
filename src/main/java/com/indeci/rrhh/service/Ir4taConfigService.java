package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ir4ta.*;
import com.indeci.rrhh.entity.Ir4taConfigAnual;
import com.indeci.rrhh.repository.Ir4taConfigAnualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Gestión de configuraciones anuales de IR 4ta Categoría (CAS).
 * Base normativa: TUO LIR Art. 33 inc. e) · D.S. 122-94-EF · SUNAT 3042.
 */
@Service
@RequiredArgsConstructor
public class Ir4taConfigService {

    private static final BigDecimal TASA_DEFAULT  = BigDecimal.valueOf(8);
    private static final BigDecimal PCT_INAFECTA  = BigDecimal.valueOf(75);
    private static final BigDecimal CIEN          = BigDecimal.valueOf(100);

    private final Ir4taConfigAnualRepository repo;

    // ── Listar ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Ir4taConfigDto> listar(String estado, boolean incluirAnulados) {
        List<Ir4taConfigAnual> rows;
        if (estado != null && !estado.isBlank()) {
            rows = repo.findByEstadoOrderByAnioDesc(estado);
        } else {
            rows = incluirAnulados
                    ? repo.findAllByOrderByAnioFiscalDesc()
                    : repo.findAllExcluyendoAnulados();
        }
        return rows.stream().map(this::toDto).toList();
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public Ir4taConfigDto crear(Ir4taConfigInputDto input, String usuario) {
        validarFechas(input.getVigenciaInicio(), input.getVigenciaFin());
        validarNoSolapamiento(input.getVigenciaInicio(), input.getVigenciaFin(), null);

        Ir4taConfigAnual c = new Ir4taConfigAnual();
        mapInput(input, c);
        c.setAnioFiscal(input.getAnioFiscal());
        c.setEstado("BORRADOR");
        c.setBloqueadoPorPlanilla(0);
        c.setCreadoPor(usuario);
        c.setCreadoEn(LocalDateTime.now());
        repo.save(c);
        return toDto(c);
    }

    // ── Editar ────────────────────────────────────────────────────────────────

    @Transactional
    public Ir4taConfigDto editar(Long id, Ir4taConfigInputDto input, String usuario) {
        Ir4taConfigAnual c = findById(id);
        if (c.getBloqueadoPorPlanilla() == 1)
            throw new NegocioException(
                "No se puede editar esta vigencia porque ya fue utilizada en planilla cerrada. " +
                "Cree una nueva vigencia para mantener la trazabilidad.");
        if ("ANULADO".equals(c.getEstado()))
            throw new NegocioException("No se puede editar una vigencia anulada.");

        validarFechas(input.getVigenciaInicio(), input.getVigenciaFin());
        validarNoSolapamiento(input.getVigenciaInicio(), input.getVigenciaFin(), id);

        mapInput(input, c);
        c.setAnioFiscal(input.getAnioFiscal());
        c.setModificadoPor(usuario);
        c.setModificadoEn(LocalDateTime.now());
        return toDto(c);
    }

    // ── Publicar (BORRADOR → VIGENTE) ─────────────────────────────────────────

    @Transactional
    public Ir4taConfigDto publicar(Long id, String usuario) {
        Ir4taConfigAnual c = findById(id);
        if (!"BORRADOR".equals(c.getEstado()))
            throw new NegocioException("Solo se puede publicar una vigencia en estado BORRADOR.");
        c.setEstado("VIGENTE");
        c.setModificadoPor(usuario);
        c.setModificadoEn(LocalDateTime.now());
        return toDto(c);
    }

    // ── Cerrar (VIGENTE → CERRADO) ────────────────────────────────────────────

    @Transactional
    public void cerrar(Long id, String usuario) {
        Ir4taConfigAnual c = findById(id);
        if (c.getBloqueadoPorPlanilla() == 1)
            throw new NegocioException("Parámetro bloqueado por planilla cerrada.");
        if (!"VIGENTE".equals(c.getEstado()))
            throw new NegocioException("Solo se puede cerrar una vigencia en estado VIGENTE.");
        c.setEstado("CERRADO");
        c.setModificadoPor(usuario);
        c.setModificadoEn(LocalDateTime.now());
    }

    // ── Duplicar ──────────────────────────────────────────────────────────────

    @Transactional
    public Ir4taConfigDto duplicar(Long id, Ir4taConfigDuplicarInputDto req, String usuario) {
        Ir4taConfigAnual orig = findById(id);
        if ("ANULADO".equals(orig.getEstado()))
            throw new NegocioException("No se puede duplicar una vigencia anulada.");

        validarFechas(req.getVigenciaInicio(), req.getVigenciaFin());
        validarNoSolapamiento(req.getVigenciaInicio(), req.getVigenciaFin(), null);

        Ir4taConfigAnual dup = new Ir4taConfigAnual();
        dup.setAnioFiscal(req.getAnioFiscal());
        dup.setVigenciaInicio(req.getVigenciaInicio());
        dup.setVigenciaFin(req.getVigenciaFin());
        dup.setUitVigente(req.getUitVigente());
        dup.setTasaIr4ta(orig.getTasaIr4ta() != null ? orig.getTasaIr4ta() : TASA_DEFAULT);
        dup.setBaseInafectaIr4ta(calcularBaseInafecta(req.getUitVigente()));
        dup.setFuenteOficial(req.getFuenteOficial());
        dup.setUrlFuenteOficial(orig.getUrlFuenteOficial());
        dup.setFechaPublicacion(orig.getFechaPublicacion());
        dup.setObservacion(req.getObservacion() != null ? req.getObservacion()
                : "[Duplicado de vigencia " + orig.getAnioFiscal() + "]");
        dup.setEstado("BORRADOR");
        dup.setBloqueadoPorPlanilla(0);
        dup.setCreadoPor(usuario);
        dup.setCreadoEn(LocalDateTime.now());
        repo.save(dup);
        return toDto(dup);
    }

    // ── Anular ────────────────────────────────────────────────────────────────

    @Transactional
    public void anular(Long id, Ir4taConfigAnularInputDto req, String usuario) {
        Ir4taConfigAnual c = findById(id);
        if ("ANULADO".equals(c.getEstado()))
            throw new NegocioException("La vigencia ya está anulada.");
        if (c.getBloqueadoPorPlanilla() == 1)
            throw new NegocioException(
                "No se puede eliminar esta vigencia porque ya fue utilizada en planilla cerrada. " +
                "El registro permanecerá protegido para mantener la trazabilidad.");
        c.setEstado("ANULADO");
        c.setMotivoAnulacion(req.getMotivo());
        c.setAnuladoPor(usuario);
        c.setAnuladoEn(LocalDateTime.now());
        c.setModificadoPor(usuario);
        c.setModificadoEn(LocalDateTime.now());
    }

    // ── Resolver por período (usado por motor y preflight) ────────────────────

    @Transactional(readOnly = true)
    public Ir4taResolverResultDto resolver(String periodo) {
        Ir4taResolverResultDto dto = new Ir4taResolverResultDto();
        dto.setPeriodoConsultado(periodo);

        LocalDate fecha = parsePeriodoToDate(periodo);
        if (fecha == null) {
            dto.setEncontrado(false);
            dto.setEstadoValidacion("CONFIG_INCOMPLETA");
            dto.setMensaje("Formato de período inválido. Use YYYYMM o YYYY-MM.");
            return dto;
        }

        List<Ir4taConfigAnual> vigencias = repo.findAplicableByFecha(fecha);
        if (vigencias.isEmpty()) {
            dto.setEncontrado(false);
            dto.setEstadoValidacion("SIN_VIGENCIA");
            dto.setMensaje(
                "No existe configuración anual de 4ta categoría vigente para el período " + periodo +
                ". Registre una nueva vigencia antes de generar la planilla.");
            return dto;
        }

        Ir4taConfigAnual v = vigencias.get(0);
        dto.setEncontrado(true);
        dto.setVigenciaId(v.getId());
        dto.setAnioFiscal(v.getAnioFiscal());
        dto.setVigenciaInicio(v.getVigenciaInicio());
        dto.setVigenciaFin(v.getVigenciaFin());
        dto.setUitVigente(v.getUitVigente());
        dto.setTasaIr4ta(v.getTasaIr4ta());
        dto.setBaseInafectaIr4ta(v.getBaseInafectaIr4ta());
        dto.setFuenteOficial(v.getFuenteOficial());
        dto.setEstadoValidacion("VALIDO");
        return dto;
    }

    /** Resolución directa por año fiscal para el motor de planilla. */
    @Transactional(readOnly = true)
    public Optional<Ir4taConfigAnual> resolverPorAnio(int anio, LocalDate fechaDevengue) {
        List<Ir4taConfigAnual> vigencias = repo.findAplicableByFecha(fechaDevengue);
        return vigencias.stream()
                .filter(v -> v.getAnioFiscal().equals(anio))
                .findFirst()
                .or(() -> vigencias.stream().findFirst());
    }

    @Transactional(readOnly = true)
    public long countVigentes() {
        return repo.countByEstado("VIGENTE");
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Ir4taConfigAnual findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Configuración IR4ta no encontrada: " + id));
    }

    private void validarFechas(LocalDate ini, LocalDate fin) {
        if (fin != null && !fin.isAfter(ini))
            throw new NegocioException("La fecha de fin debe ser posterior a la fecha de inicio.");
    }

    private void validarNoSolapamiento(LocalDate ini, LocalDate fin, Long idExcluir) {
        long solapados = repo.countSolapamiento(ini, fin, idExcluir);
        if (solapados > 0)
            throw new NegocioException(
                "El rango de vigencia se solapa con otra configuración IR4ta activa. " +
                "Ya existe una vigencia que cubre el período seleccionado.");
    }

    private BigDecimal calcularBaseInafecta(BigDecimal uit) {
        if (uit == null) return null;
        return uit.multiply(PCT_INAFECTA).divide(CIEN, 2, java.math.RoundingMode.HALF_UP);
    }

    private void mapInput(Ir4taConfigInputDto input, Ir4taConfigAnual c) {
        c.setVigenciaInicio(input.getVigenciaInicio());
        c.setVigenciaFin(input.getVigenciaFin());
        c.setUitVigente(input.getUitVigente());
        c.setTasaIr4ta(input.getTasaIr4ta() != null ? input.getTasaIr4ta() : TASA_DEFAULT);
        c.setBaseInafectaIr4ta(input.getBaseInafectaIr4ta() != null
                ? input.getBaseInafectaIr4ta()
                : calcularBaseInafecta(input.getUitVigente()));
        c.setFuenteOficial(input.getFuenteOficial());
        c.setUrlFuenteOficial(input.getUrlFuenteOficial());
        c.setFechaPublicacion(input.getFechaPublicacion());
        c.setObservacion(input.getObservacion());
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

    private Ir4taConfigDto toDto(Ir4taConfigAnual c) {
        Ir4taConfigDto dto = new Ir4taConfigDto();
        dto.setId(c.getId());
        dto.setAnioFiscal(c.getAnioFiscal());
        dto.setVigenciaInicio(c.getVigenciaInicio());
        dto.setVigenciaFin(c.getVigenciaFin());
        dto.setUitVigente(c.getUitVigente());
        dto.setTasaIr4ta(c.getTasaIr4ta());
        dto.setBaseInafectaIr4ta(c.getBaseInafectaIr4ta());
        dto.setFuenteOficial(c.getFuenteOficial());
        dto.setUrlFuenteOficial(c.getUrlFuenteOficial());
        dto.setFechaPublicacion(c.getFechaPublicacion());
        dto.setObservacion(c.getObservacion());
        dto.setEstado(c.getEstado());
        dto.setBloqueadoPorPlanilla(c.getBloqueadoPorPlanilla() == 1);
        dto.setCreadoPor(c.getCreadoPor());
        dto.setCreadoEn(c.getCreadoEn());
        dto.setModificadoPor(c.getModificadoPor());
        dto.setModificadoEn(c.getModificadoEn());
        return dto;
    }
}
