package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoSaludEpsAnularInputDto;
import com.indeci.rrhh.dto.EmpleadoSaludEpsDto;
import com.indeci.rrhh.dto.EmpleadoSaludEpsInputDto;
import com.indeci.rrhh.dto.EpsDto;
import com.indeci.rrhh.entity.EmpleadoSaludEps;
import com.indeci.rrhh.entity.Eps;
import com.indeci.rrhh.repository.EmpleadoSaludEpsRepository;
import com.indeci.rrhh.repository.EpsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpleadoSaludEpsService {

    private final EmpleadoSaludEpsRepository repo;
    private final EpsRepository epsRepo;

    // ── Catálogo EPS ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EpsDto> listarEps() {
        return epsRepo.findByActivoOrderByNombreAsc(1)
                .stream().map(this::toEpsDto).toList();
    }

    // ── Historial completo por empleado ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EmpleadoSaludEpsDto> historial(Long empleadoId) {
        Map<Long, String> epsMap = buildEpsMap();
        return repo.findByEmpleadoIdOrderByFechaInicioDesc(empleadoId)
                .stream().map(e -> toDto(e, epsMap)).toList();
    }

    // ── Cobertura actual (ACTIVO) ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EmpleadoSaludEpsDto actual(Long empleadoId) {
        Map<Long, String> epsMap = buildEpsMap();
        return repo.findFirstByEmpleadoIdAndEstadoOrderByFechaInicioDesc(empleadoId, "ACTIVO")
                .map(e -> toDto(e, epsMap))
                .orElse(null);
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public EmpleadoSaludEpsDto crear(Long empleadoId, EmpleadoSaludEpsInputDto input, String usuario) {
        validarInput(input);
        validarNoSolapamiento(empleadoId, input.getFechaInicio(), input.getFechaFin(), null);

        // Si ya existe una cobertura ACTIVA y la nueva tiene fecha inicio posterior, cerrar la anterior
        repo.findFirstByEmpleadoIdAndEstadoOrderByFechaInicioDesc(empleadoId, "ACTIVO")
                .ifPresent(prev -> {
                    if (!input.getFechaInicio().isAfter(prev.getFechaInicio())) return;
                    if (prev.getFechaFin() == null || prev.getFechaFin().isAfter(input.getFechaInicio())) {
                        prev.setFechaFin(input.getFechaInicio().minusDays(1));
                        prev.setEstado("CERRADO");
                        prev.setModificadoPor(usuario);
                        prev.setModificadoEn(LocalDateTime.now());
                    }
                });

        EmpleadoSaludEps e = new EmpleadoSaludEps();
        e.setEmpleadoId(empleadoId);
        mapInput(input, e);
        e.setEstado("ACTIVO");
        e.setCreadoPor(usuario);
        e.setCreadoEn(LocalDateTime.now());
        repo.save(e);

        Map<Long, String> epsMap = buildEpsMap();
        return toDto(e, epsMap);
    }

    // ── Editar ────────────────────────────────────────────────────────────────

    @Transactional
    public EmpleadoSaludEpsDto editar(Long empleadoId, Long id, EmpleadoSaludEpsInputDto input, String usuario) {
        EmpleadoSaludEps e = findByIdAndEmpleado(id, empleadoId);
        if ("ANULADO".equals(e.getEstado()))
            throw new NegocioException("No se puede editar una cobertura anulada.");

        validarInput(input);
        validarNoSolapamiento(empleadoId, input.getFechaInicio(), input.getFechaFin(), id);
        mapInput(input, e);
        e.setModificadoPor(usuario);
        e.setModificadoEn(LocalDateTime.now());

        Map<Long, String> epsMap = buildEpsMap();
        return toDto(e, epsMap);
    }

    // ── Cerrar ────────────────────────────────────────────────────────────────

    @Transactional
    public void cerrar(Long empleadoId, Long id, String usuario) {
        EmpleadoSaludEps e = findByIdAndEmpleado(id, empleadoId);
        if (!"ACTIVO".equals(e.getEstado()))
            throw new NegocioException("Solo se puede cerrar una cobertura en estado ACTIVO.");
        e.setEstado("CERRADO");
        e.setModificadoPor(usuario);
        e.setModificadoEn(LocalDateTime.now());
    }

    // ── Anular ────────────────────────────────────────────────────────────────

    @Transactional
    public void anular(Long empleadoId, Long id, EmpleadoSaludEpsAnularInputDto req, String usuario) {
        EmpleadoSaludEps e = findByIdAndEmpleado(id, empleadoId);
        if ("ANULADO".equals(e.getEstado()))
            throw new NegocioException("La cobertura ya está anulada.");
        e.setEstado("ANULADO");
        e.setMotivoAnulacion(req.getMotivo());
        e.setAnuladoPor(usuario);
        e.setAnuladoEn(LocalDateTime.now());
        e.setModificadoPor(usuario);
        e.setModificadoEn(LocalDateTime.now());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmpleadoSaludEps findByIdAndEmpleado(Long id, Long empleadoId) {
        EmpleadoSaludEps e = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cobertura Salud/EPS no encontrada: " + id));
        if (!e.getEmpleadoId().equals(empleadoId))
            throw new NegocioException("La cobertura no pertenece al empleado indicado.");
        return e;
    }

    private void validarInput(EmpleadoSaludEpsInputDto input) {
        if (!"ESSALUD".equals(input.getTipoCobertura()) && !"ESSALUD_EPS".equals(input.getTipoCobertura()))
            throw new NegocioException("Tipo de cobertura inválido. Use ESSALUD o ESSALUD_EPS.");
        if ("ESSALUD_EPS".equals(input.getTipoCobertura()) && input.getEpsId() == null)
            throw new NegocioException("Debe seleccionar una EPS cuando la cobertura es EsSalud + EPS.");
        if (input.getFechaFin() != null && !input.getFechaFin().isAfter(input.getFechaInicio()))
            throw new NegocioException("La fecha de fin debe ser posterior a la fecha de inicio.");
        if ("ESSALUD".equals(input.getTipoCobertura()))
            input.setEpsId(null);
    }

    private void validarNoSolapamiento(Long empleadoId, java.time.LocalDate inicio,
                                        java.time.LocalDate fin, Long idExcluir) {
        long solapados = repo.countSolapamiento(empleadoId, inicio, fin, idExcluir);
        if (solapados > 0)
            throw new NegocioException(
                "El rango de fechas se solapa con otra cobertura activa o cerrada del mismo empleado.");
    }

    private void mapInput(EmpleadoSaludEpsInputDto input, EmpleadoSaludEps e) {
        e.setTipoCobertura(input.getTipoCobertura());
        e.setEpsId(input.getEpsId());
        e.setFechaInicio(input.getFechaInicio());
        e.setFechaFin(input.getFechaFin());
        e.setDocumentoSustento(input.getDocumentoSustento());
        e.setObservacion(input.getObservacion());
    }

    private Map<Long, String> buildEpsMap() {
        return epsRepo.findAll().stream()
                .collect(Collectors.toMap(Eps::getId, Eps::getNombre));
    }

    private EmpleadoSaludEpsDto toDto(EmpleadoSaludEps e, Map<Long, String> epsMap) {
        EmpleadoSaludEpsDto dto = new EmpleadoSaludEpsDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setTipoCobertura(e.getTipoCobertura());
        dto.setEpsId(e.getEpsId());
        dto.setEpsNombre(e.getEpsId() != null ? epsMap.get(e.getEpsId()) : null);
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setEstado(e.getEstado());
        dto.setDocumentoSustento(e.getDocumentoSustento());
        dto.setObservacion(e.getObservacion());
        dto.setMotivoAnulacion(e.getMotivoAnulacion());
        dto.setAnuladoPor(e.getAnuladoPor());
        dto.setAnuladoEn(e.getAnuladoEn());
        dto.setCreadoPor(e.getCreadoPor());
        dto.setCreadoEn(e.getCreadoEn());
        dto.setModificadoPor(e.getModificadoPor());
        dto.setModificadoEn(e.getModificadoEn());
        return dto;
    }

    private EpsDto toEpsDto(Eps eps) {
        EpsDto dto = new EpsDto();
        dto.setId(eps.getId());
        dto.setCodigo(eps.getCodigo());
        dto.setNombre(eps.getNombre());
        dto.setActivo(eps.getActivo() == 1);
        return dto;
    }
}
