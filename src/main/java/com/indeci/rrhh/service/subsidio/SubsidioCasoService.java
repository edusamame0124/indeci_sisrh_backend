package com.indeci.rrhh.service.subsidio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.subsidio.SubsidioBaseDetalleDto;
import com.indeci.rrhh.dto.subsidio.SubsidioBaseHistoricaResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCasoDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCasoPageDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCasoResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCittDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCittResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioLiquidacionResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioTimelineEventoDto;
import com.indeci.rrhh.dto.subsidio.SubsidioTramoResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioValidacionDto;
import com.indeci.rrhh.entity.SubsidioBaseDetalle;
import com.indeci.rrhh.entity.SubsidioBaseHistorica;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioCitt;
import com.indeci.rrhh.entity.SubsidioLiquidacion;
import com.indeci.rrhh.entity.SubsidioTimelineEvento;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.repository.SubsidioCittRepository;
import com.indeci.rrhh.repository.SubsidioTimelineEventoRepository;
import com.indeci.rrhh.subsidio.SubsidioEstados;
import com.indeci.rrhh.subsidio.SubsidioPeriodoUtil;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioCasoService {

    private static final Set<String> TIPOS = Set.of(
            SubsidioEstados.TIPO_ENFERMEDAD, SubsidioEstados.TIPO_MATERNIDAD);
    private static final Set<String> ESTADOS_EDITABLES = Set.of(
            SubsidioEstados.CASO_BORRADOR, SubsidioEstados.CASO_PENDIENTE_VALIDACION);

    private final SubsidioCasoRepository casoRepository;
    private final SubsidioCittRepository cittRepository;
    private final SubsidioTramoService tramoService;
    private final SubsidioBaseHistoricaService baseHistoricaService;
    private final SubsidioLiquidacionService liquidacionService;
    private final SubsidioPlanillaIntegracionService planillaIntegracionService;
    private final SubsidioValidacionService validacionService;
    private final SubsidioTimelineService timelineService;
    private final SubsidioTimelineEventoRepository timelineRepository;
    private final EmpleadoRepository empleadoRepository;

    @Transactional(readOnly = true)
    public SubsidioCasoPageDto listar(
            String periodo, String tipo, String estado,
            Long empleadoId, String dni, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        String periodoNorm = periodo != null ? SubsidioPeriodoUtil.aSubsidio(periodo) : null;
        Page<SubsidioCaso> result = dni != null && !dni.isBlank()
                ? casoRepository.findBandejaConDni(
                        periodoNorm, tipo, estado, empleadoId, dni.trim(),
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                : casoRepository.findBandeja(
                        periodoNorm, tipo, estado, empleadoId,
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<Long, String[]> personas = cachePersonas(result.getContent());
        List<SubsidioCasoResponseDto> content = result.getContent().stream()
                .map(c -> toResponseResumen(c, personas))
                .toList();
        return new SubsidioCasoPageDto(
                content, result.getTotalElements(), result.getTotalPages(),
                result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public SubsidioCasoResponseDto obtener(Long id) {
        SubsidioCaso caso = buscar(id);
        Map<Long, String[]> personas = cachePersonas(List.of(caso));
        return toResponseDetalle(caso, personas);
    }

    @Transactional
    public SubsidioCasoResponseDto crear(SubsidioCasoDto dto) {
        validarDto(dto);
        SubsidioCaso caso = new SubsidioCaso();
        caso.setEmpleadoId(dto.empleadoId());
        caso.setCodigoCaso(generarCodigoCaso(dto.empleadoId()));
        caso.setTipoCaso(dto.tipoCaso().toUpperCase());
        caso.setEstado(SubsidioEstados.CASO_BORRADOR);
        caso.setFechaContingencia(dto.fechaContingencia());
        caso.setFechaInicio(dto.fechaInicio());
        caso.setFechaFin(dto.fechaFin());
        caso.setDiasContingencia(calcularDias(dto.fechaInicio(), dto.fechaFin()));
        caso.setVersionCaso(1);
        caso.setModoCalculo(dto.modoCalculo() != null ? dto.modoCalculo() : SubsidioEstados.MODO_OFICIAL);
        caso.setObservacion(dto.observacion());
        caso.setActivo(1);
        caso.setCreatedAt(LocalDateTime.now());
        caso.setCreatedBy(SecurityUtil.getUsername());
        caso = casoRepository.save(caso);
        timelineService.registrar(caso.getId(), "CREACION", "Caso registrado en borrador", caso.getId());
        return obtener(caso.getId());
    }

    @Transactional
    public SubsidioCasoResponseDto actualizar(Long id, SubsidioCasoDto dto) {
        SubsidioCaso caso = buscar(id);
        assertEditable(caso);
        validarDto(dto);
        caso.setFechaContingencia(dto.fechaContingencia());
        caso.setFechaInicio(dto.fechaInicio());
        caso.setFechaFin(dto.fechaFin());
        caso.setDiasContingencia(calcularDias(dto.fechaInicio(), dto.fechaFin()));
        caso.setObservacion(dto.observacion());
        if (dto.modoCalculo() != null) {
            caso.setModoCalculo(dto.modoCalculo());
        }
        caso.setModifiedAt(LocalDateTime.now());
        caso.setModifiedBy(SecurityUtil.getUsername());
        casoRepository.save(caso);
        timelineService.registrar(id, "ACTUALIZACION", "Datos del caso actualizados", id);
        return obtener(id);
    }

    @Transactional
    public SubsidioCasoResponseDto cambiarEstado(Long id, String estado) {
        SubsidioCaso caso = buscar(id);
        caso.setEstado(estado.toUpperCase());
        caso.setModifiedAt(LocalDateTime.now());
        caso.setModifiedBy(SecurityUtil.getUsername());
        casoRepository.save(caso);
        timelineService.registrar(id, "CAMBIO_ESTADO", "Estado → " + estado, id);
        return obtener(id);
    }

    @Transactional
    public SubsidioCittResponseDto registrarCitt(Long casoId, SubsidioCittDto dto) {
        buscar(casoId);
        if (dto.fechaFin().isBefore(dto.fechaInicio())) {
            throw new NegocioException("CITT con fecha fin anterior a inicio");
        }
        SubsidioCitt citt = new SubsidioCitt();
        citt.setCasoId(casoId);
        citt.setNroCitt(dto.nroCitt().trim());
        citt.setFechaEmision(dto.fechaEmision());
        citt.setFechaInicio(dto.fechaInicio());
        citt.setFechaFin(dto.fechaFin());
        citt.setEstado("REGISTRADO");
        citt.setTipoDocumento(dto.tipoDocumento());
        citt.setHashDocumento(dto.hashDocumento());
        citt.setLegajoDocId(dto.legajoDocId());
        citt.setAccesoRestringido(
                dto.accesoRestringido() != null ? dto.accesoRestringido() : "S");
        citt.setActivo(1);
        citt.setCreatedAt(LocalDateTime.now());
        citt.setCreatedBy(SecurityUtil.getUsername());
        citt = cittRepository.save(citt);
        timelineService.registrar(casoId, "CITT_REGISTRADO", "CITT " + citt.getNroCitt(), citt.getId());
        return toCittResponse(citt);
    }

    @Transactional
    public SubsidioCittResponseDto actualizarCitt(Long cittId, SubsidioCittDto dto) {
        SubsidioCitt citt = cittRepository.findByIdAndActivo(cittId, 1)
                .orElseThrow(() -> new NegocioException("CITT no encontrado"));
        citt.setNroCitt(dto.nroCitt().trim());
        citt.setFechaEmision(dto.fechaEmision());
        citt.setFechaInicio(dto.fechaInicio());
        citt.setFechaFin(dto.fechaFin());
        citt.setTipoDocumento(dto.tipoDocumento());
        citt.setHashDocumento(dto.hashDocumento());
        citt.setLegajoDocId(dto.legajoDocId());
        if (dto.accesoRestringido() != null) {
            citt.setAccesoRestringido(dto.accesoRestringido());
        }
        citt = cittRepository.save(citt);
        timelineService.registrar(citt.getCasoId(), "CITT_ACTUALIZADO", "CITT " + citt.getNroCitt(), cittId);
        return toCittResponse(citt);
    }

    @Transactional(readOnly = true)
    public List<SubsidioCittResponseDto> listarCitt(Long casoId) {
        return cittRepository.findByCasoIdAndActivoOrderByFechaInicioAsc(casoId, 1).stream()
                .map(this::toCittResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubsidioTimelineEventoDto> timeline(Long casoId) {
        return timelineRepository.findByCasoIdOrderByCreatedAtDesc(casoId).stream()
                .map(this::toTimelineDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubsidioValidacionDto> validaciones(Long casoId) {
        return validacionService.validarCaso(casoId);
    }

    @Transactional(readOnly = true)
    public SubsidioBaseHistoricaResponseDto obtenerBaseHistorica(Long casoId) {
        SubsidioBaseHistorica base = baseHistoricaService.obtenerVigente(casoId);
        if (base == null) {
            return null;
        }
        return toBaseResponse(base);
    }

    @Transactional
    public SubsidioBaseHistoricaResponseDto calcularBaseHistorica(Long casoId) {
        return toBaseResponse(baseHistoricaService.calcular(casoId));
    }

    @Transactional
    public List<SubsidioTramoResponseDto> generarTramos(Long casoId) {
        return tramoService.generarTramos(casoId).stream()
                .map(this::toTramoResponse)
                .toList();
    }

    @Transactional
    public SubsidioTramoResponseDto actualizarTramo(
            Long tramoId, Integer diasSubsidio, Integer diasLaborados) {
        return toTramoResponse(tramoService.actualizar(tramoId, diasSubsidio, diasLaborados));
    }

    @Transactional
    public SubsidioLiquidacionResponseDto calcularLiquidacion(Long tramoId) {
        return toLiquidacionResponse(liquidacionService.calcular(tramoId));
    }

    @Transactional(readOnly = true)
    public List<SubsidioLiquidacionResponseDto> historialLiquidaciones(Long tramoId) {
        return liquidacionService.historial(tramoId).stream()
                .map(this::toLiquidacionResponse)
                .toList();
    }

    @Transactional
    public SubsidioLiquidacionResponseDto aplicarPlanilla(Long liquidacionId) {
        return toLiquidacionResponse(planillaIntegracionService.aplicarPlanilla(liquidacionId));
    }

    @Transactional
    public SubsidioLiquidacionResponseDto revertirPlanilla(Long liquidacionId, String motivo) {
        return toLiquidacionResponse(planillaIntegracionService.revertir(liquidacionId, motivo));
    }

    private SubsidioCaso buscar(Long id) {
        return casoRepository.findByIdAndActivo(id, 1)
                .orElseThrow(() -> new NegocioException("Caso de subsidio no encontrado"));
    }

    private void assertEditable(SubsidioCaso caso) {
        if (!ESTADOS_EDITABLES.contains(caso.getEstado())) {
            throw new NegocioException(
                    "El caso en estado " + caso.getEstado() + " no permite edición de campos críticos");
        }
    }

    private void validarDto(SubsidioCasoDto dto) {
        if (dto.empleadoId() == null) {
            throw new NegocioException("empleadoId requerido");
        }
        if (dto.tipoCaso() == null || !TIPOS.contains(dto.tipoCaso().toUpperCase())) {
            throw new NegocioException("tipoCaso debe ser ENFERMEDAD o MATERNIDAD");
        }
        if (dto.fechaInicio() == null || dto.fechaFin() == null) {
            throw new NegocioException("fechaInicio y fechaFin son obligatorias");
        }
        if (dto.fechaFin().isBefore(dto.fechaInicio())) {
            throw new NegocioException("fechaFin no puede ser anterior a fechaInicio");
        }
        if (!empleadoRepository.existsById(dto.empleadoId())) {
            throw new NegocioException("Empleado no encontrado");
        }
    }

    private String generarCodigoCaso(Long empleadoId) {
        long seq = casoRepository.countByEmpleadoIdAndActivo(empleadoId, 1) + 1;
        int anio = LocalDate.now().getYear();
        return String.format("SUB-%d-%05d", anio, seq);
    }

    private static int calcularDias(LocalDate inicio, LocalDate fin) {
        return (int) ChronoUnit.DAYS.between(inicio, fin) + 1;
    }

    private Map<Long, String[]> cachePersonas(List<SubsidioCaso> casos) {
        List<Long> ids = casos.stream().map(SubsidioCaso::getEmpleadoId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String[]> map = new HashMap<>();
        for (Object[] row : empleadoRepository.findPersonaResumenByEmpleadoIds(ids)) {
            map.put(((Number) row[0]).longValue(), new String[]{
                    row[1] != null ? row[1].toString() : "",
                    row[2] != null ? row[2].toString() : ""});
        }
        return map;
    }

    private SubsidioCasoResponseDto toResponseResumen(SubsidioCaso c, Map<Long, String[]> personas) {
        String[] p = personas.getOrDefault(c.getEmpleadoId(), new String[]{"", ""});
        return new SubsidioCasoResponseDto(
                c.getId(), c.getEmpleadoId(), c.getCodigoCaso(), c.getTipoCaso(), c.getEstado(),
                c.getFechaContingencia(), c.getFechaInicio(), c.getFechaFin(), c.getDiasContingencia(),
                c.getVersionCaso(), c.getReglaVigenciaId(), c.getModoCalculo(), c.getObservacion(),
                p[0], p[1], c.getCreatedAt(), List.of(), List.of());
    }

    private SubsidioCasoResponseDto toResponseDetalle(SubsidioCaso c, Map<Long, String[]> personas) {
        String[] p = personas.getOrDefault(c.getEmpleadoId(), new String[]{"", ""});
        List<SubsidioCittResponseDto> citts = listarCitt(c.getId());
        List<SubsidioTramoResponseDto> tramos = tramoService.listarPorCaso(c.getId()).stream()
                .map(this::toTramoResponse)
                .toList();
        return new SubsidioCasoResponseDto(
                c.getId(), c.getEmpleadoId(), c.getCodigoCaso(), c.getTipoCaso(), c.getEstado(),
                c.getFechaContingencia(), c.getFechaInicio(), c.getFechaFin(), c.getDiasContingencia(),
                c.getVersionCaso(), c.getReglaVigenciaId(), c.getModoCalculo(), c.getObservacion(),
                p[0], p[1], c.getCreatedAt(), citts, tramos);
    }

    private SubsidioCittResponseDto toCittResponse(SubsidioCitt c) {
        return new SubsidioCittResponseDto(
                c.getId(), c.getCasoId(), c.getNroCitt(), c.getFechaEmision(),
                c.getFechaInicio(), c.getFechaFin(), c.getEstado(), c.getTipoDocumento(),
                c.getAccesoRestringido(), c.getCreatedAt());
    }

    private SubsidioTramoResponseDto toTramoResponse(SubsidioTramo t) {
        return new SubsidioTramoResponseDto(
                t.getId(), t.getCasoId(), t.getPeriodo(), t.getFechaDesde(), t.getFechaHasta(),
                t.getDiasSubsidio(), t.getDiasLaborados(), t.getEstadoTramo(), t.getVersionTramo());
    }

    private SubsidioLiquidacionResponseDto toLiquidacionResponse(SubsidioLiquidacion l) {
        return new SubsidioLiquidacionResponseDto(
                l.getId(), l.getTramoId(), l.getVersionLiq(), l.getEstado(),
                l.getContraprestacionDiaria(), l.getContraprestacionEquivalente(),
                l.getSubsidioDiarioEssalud(), l.getSubsidioEstimado(), l.getDiferencialIndeci(),
                l.getConciliacionTotal(), l.getFormulaAplicada(), l.getCreatedAt());
    }

    private SubsidioBaseHistoricaResponseDto toBaseResponse(SubsidioBaseHistorica base) {
        List<SubsidioBaseDetalleDto> det = baseHistoricaService.listarDetalle(base.getId()).stream()
                .map(this::toBaseDetalleDto)
                .toList();
        return new SubsidioBaseHistoricaResponseDto(
                base.getId(), base.getCasoId(), base.getMesesEvaluados(), base.getDivisorPromedio(),
                base.getTopeMensual(), base.getBaseReconocida(), base.getFuente(),
                base.getVersionBase(), base.getCreatedAt(), det);
    }

    private SubsidioBaseDetalleDto toBaseDetalleDto(SubsidioBaseDetalle d) {
        return new SubsidioBaseDetalleDto(
                d.getPeriodo(), d.getRemuneracionReal(), d.getTopeAplicado(),
                d.getBaseComputable(), d.getFuenteMovimientoId());
    }

    private SubsidioTimelineEventoDto toTimelineDto(SubsidioTimelineEvento e) {
        return new SubsidioTimelineEventoDto(
                e.getId(), e.getTipoEvento(), e.getDescripcion(), e.getUsuario(), e.getCreatedAt());
    }
}
