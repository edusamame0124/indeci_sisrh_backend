package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpMetaAnualDto;
import com.indeci.rrhh.dto.EmpMetaAnualResponse;
import com.indeci.rrhh.dto.EmpMetaTrazabilidadPageDto;
import com.indeci.rrhh.dto.EmpMetaTrazabilidadResponse;
import com.indeci.rrhh.dto.MetaPptoResumenDto;
import com.indeci.rrhh.entity.EmpMetaAnual;
import com.indeci.rrhh.entity.MetaPptoCat;
import com.indeci.rrhh.entity.MetaPptoAud;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.repository.EmpMetaAnualRepository;
import com.indeci.rrhh.repository.MetaPptoCatRepository;
import com.indeci.rrhh.repository.MetaPptoAudRepository;
import com.indeci.rrhh.repository.MetaPptoEquivRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpMetaAnualService {

    private final EmpMetaAnualRepository empMetaRepo;
    private final MetaPptoCatRepository catRepo;
    private final MetaPptoAudRepository audRepo;
    private final MetaPptoEquivRepository equivRepo;
    private final EmpleadoRepository empleadoRepo;
    private final PersonaRepository personaRepo;

    // ========================= CONSULTAS =========================

    @Transactional(readOnly = true)
    public List<EmpMetaAnualResponse> listarPorEmpleado(Long empleadoId) {
        return empMetaRepo.findByEmpleadoIdOrderByAnioFiscalDesc(empleadoId)
                .stream().map(e -> toResponse(e, null)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmpMetaAnualResponse> listarPorAnioYEstado(Integer anioFiscal, String estado) {
        List<EmpMetaAnual> lista = (estado != null && !estado.isBlank())
                ? empMetaRepo.findByAnioFiscalAndEstadoOrderByEmpleadoId(anioFiscal, estado)
                : empMetaRepo.findByAnioFiscalAndEstadoNotOrderByEmpleadoId(anioFiscal, "ANULADO");
        return lista.stream().map(e -> toResponse(e, null)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmpMetaAnualResponse obtener(Long id) {
        EmpMetaAnual e = findOrThrow(id);
        return toResponse(e, null);
    }

    @Transactional(readOnly = true)
    public Optional<EmpMetaAnualResponse> obtenerVigente(Long empleadoId, Integer anioFiscal) {
        return empMetaRepo.findVigenteByEmpleadoAndAnio(empleadoId, anioFiscal)
                .map(e -> toResponse(e, null));
    }

    /** Consulta paginada enriquecida para trazabilidad — solo lectura. */
    @Transactional(readOnly = true)
    public EmpMetaTrazabilidadPageDto trazabilidad(Integer anioFiscal, String estado,
                                                    String busqueda, String centroCosto,
                                                    int pagina, int tamanio) {
        String busquedaPct  = (busqueda   != null && !busqueda.isBlank())   ? "%" + busqueda.toLowerCase()   + "%" : null;
        String centroPct    = (centroCosto != null && !centroCosto.isBlank()) ? "%" + centroCosto.toLowerCase() + "%" : null;
        String estadoFiltro = (estado     != null && !estado.isBlank())     ? estado : null;

        Page<Object[]> page = empMetaRepo.findTrazabilidad(
                anioFiscal, estadoFiltro, busquedaPct, centroPct,
                PageRequest.of(pagina, tamanio));

        List<EmpMetaTrazabilidadResponse> content = page.getContent().stream()
                .map(row -> toTrazabilidad(row))
                .collect(Collectors.toList());

        return new EmpMetaTrazabilidadPageDto(
                content, page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }

    /** Para el motor de planilla: devuelve la meta PUBLICADA del empleado en el año. */
    @Transactional(readOnly = true)
    public Optional<EmpMetaAnualResponse> obtenerParaPlanilla(Long empleadoId, Integer anioFiscal) {
        return empMetaRepo.findPublicadaByEmpleadoAndAnio(empleadoId, anioFiscal)
                .map(e -> toResponse(e, null));
    }

    @Transactional(readOnly = true)
    public MetaPptoResumenDto resumen(Integer anioFiscal) {
        long totalCat        = catRepo.countPublicadasByAnio(anioFiscal);
        long totalEquiv      = equivRepo.countActivasByAnioDestino(anioFiscal);
        long totalAsignados  = empMetaRepo.countPublicadasByAnio(anioFiscal);
        long metasConEmp     = empMetaRepo.findMetaIdsConAsignaciones(anioFiscal).size();
        // totalEmpleadosSinMeta: metas publicadas sin ninguna asignación publicada
        long sinMeta         = totalCat - Math.min(metasConEmp, totalCat);
        String estado = totalAsignados > 0
                ? (sinMeta > 0 ? "PARCIAL" : "CON_METAS")
                : "SIN_METAS";
        return new MetaPptoResumenDto(anioFiscal, totalCat, totalCat, totalAsignados, totalAsignados,
                sinMeta, totalEquiv, estado);
    }

    // ========================= CRUD MANUAL =========================

    @Transactional
    public EmpMetaAnualResponse asignar(EmpMetaAnualDto dto, String usuario) {
        validarDto(dto);
        // Solo 1 asignación vigente por empleado/año
        if (empMetaRepo.existsByEmpleadoIdAndAnioFiscalAndEstadoNot(dto.getEmpleadoId(), dto.getAnioFiscal(), "ANULADO")) {
            throw new NegocioException("El empleado ya tiene una asignación vigente para el año " + dto.getAnioFiscal()
                    + ". Anule la existente antes de crear una nueva.");
        }
        MetaPptoCat cat = catRepo.findById(dto.getMetaPptoCatId())
                .orElseThrow(() -> new NegocioException("Meta presupuestal no encontrada: " + dto.getMetaPptoCatId()));

        EmpMetaAnual ema = new EmpMetaAnual();
        ema.setEmpleadoId(dto.getEmpleadoId());
        ema.setAnioFiscal(dto.getAnioFiscal());
        ema.setMetaPptoCatId(dto.getMetaPptoCatId());
        ema.setVigenciaInicio(dto.getVigenciaInicio() != null ? dto.getVigenciaInicio() : LocalDate.of(dto.getAnioFiscal(), 1, 1));
        ema.setVigenciaFin(dto.getVigenciaFin());
        ema.setObservacion(dto.getObservacion());
        ema.setEstado("BORRADOR");
        ema.setOrigen("MANUAL");
        ema.setBloqueadoPorPlanilla(0);
        ema.setCreadoPor(usuario);
        ema.setCreadoEn(LocalDateTime.now());
        // Snapshot del catálogo
        ema.setCentroCosto(cat.getCentroCosto());
        ema.setCategoriaPresupuestal(cat.getCategoriaPresupuestal());
        ema.setProducto(cat.getProducto());
        ema.setActividad(cat.getActividad());
        ema.setFinalidad(cat.getFinalidad());
        // Snapshot del empleado
        empleadoRepo.findById(dto.getEmpleadoId()).ifPresent(emp -> {
            if (emp.getPersonaId() != null) {
                personaRepo.findById(emp.getPersonaId()).ifPresent(p -> {
                    ema.setDni(p.getDni());
                    ema.setNombres(p.getNombreCompleto());
                });
            }
        });
        EmpMetaAnual saved = empMetaRepo.save(ema);
        registrarAud(saved.getId(), dto.getEmpleadoId(), dto.getAnioFiscal(), null, saved, "CREAR_ASIGNACION", null, usuario);
        return toResponse(saved, cat);
    }

    @Transactional
    public EmpMetaAnualResponse editar(Long id, EmpMetaAnualDto dto, String usuario) {
        EmpMetaAnual ema = findOrThrow(id);
        if (ema.getBloqueadoPorPlanilla() != null && ema.getBloqueadoPorPlanilla() == 1) {
            throw new NegocioException("La asignación está bloqueada por una planilla cerrada.");
        }
        if ("PUBLICADO".equals(ema.getEstado()) || "CERRADO".equals(ema.getEstado())) {
            throw new NegocioException("No se puede editar una asignación en estado " + ema.getEstado());
        }
        String anterior = toJson(ema);
        if (dto.getMetaPptoCatId() != null) {
            catRepo.findById(dto.getMetaPptoCatId())
                    .orElseThrow(() -> new NegocioException("Meta presupuestal no encontrada: " + dto.getMetaPptoCatId()));
            ema.setMetaPptoCatId(dto.getMetaPptoCatId());
        }
        if (dto.getVigenciaInicio() != null) ema.setVigenciaInicio(dto.getVigenciaInicio());
        ema.setVigenciaFin(dto.getVigenciaFin());
        ema.setObservacion(dto.getObservacion());
        ema.setModificadoPor(usuario);
        ema.setModificadoEn(LocalDateTime.now());
        EmpMetaAnual saved = empMetaRepo.save(ema);
        registrarAud(id, ema.getEmpleadoId(), ema.getAnioFiscal(), anterior, saved, "EDITAR_ASIGNACION", null, usuario);
        return toResponse(saved, null);
    }

    @Transactional
    public void anular(Long id, String motivo, String usuario) {
        EmpMetaAnual ema = findOrThrow(id);
        if ("ANULADO".equals(ema.getEstado())) {
            throw new NegocioException("La asignación ya está anulada.");
        }
        if (ema.getBloqueadoPorPlanilla() != null && ema.getBloqueadoPorPlanilla() == 1) {
            throw new NegocioException("La asignación está bloqueada por una planilla cerrada y no puede anularse.");
        }
        String anterior = toJson(ema);
        ema.setEstado("ANULADO");
        ema.setMotivoAnulacion(motivo);
        ema.setAnuladoPor(usuario);
        ema.setAnuladoEn(LocalDateTime.now());
        empMetaRepo.save(ema);
        registrarAud(id, ema.getEmpleadoId(), ema.getAnioFiscal(), anterior, null, "ANULAR_ASIGNACION", motivo, usuario);
    }

    /** Bloquear asignación al cerrar una planilla. Solo llamado por el motor. */
    @Transactional
    public void bloquearPorPlanilla(Long empleadoId, Integer anioFiscal, String usuario) {
        int rows = empMetaRepo.bloquearPorPlanilla(empleadoId, anioFiscal);
        if (rows > 0) {
            registrarAud(null, empleadoId, anioFiscal, null, null, "BLOQUEAR_POR_PLANILLA", null, usuario);
        }
    }

    // ========================= HELPERS =========================

    private EmpMetaAnual findOrThrow(Long id) {
        return empMetaRepo.findById(id)
                .orElseThrow(() -> new NegocioException("Asignación de meta no encontrada: " + id));
    }

    private void validarDto(EmpMetaAnualDto dto) {
        if (dto.getEmpleadoId() == null) throw new NegocioException("El empleado es obligatorio.");
        if (dto.getAnioFiscal() == null) throw new NegocioException("El año fiscal es obligatorio.");
        if (dto.getMetaPptoCatId() == null) throw new NegocioException("La meta presupuestal es obligatoria.");
    }

    EmpMetaAnualResponse toResponse(EmpMetaAnual e, MetaPptoCat catOpt) {
        EmpMetaAnualResponse r = new EmpMetaAnualResponse();
        r.setId(e.getId());
        r.setEmpleadoId(e.getEmpleadoId());
        r.setAnioFiscal(e.getAnioFiscal());
        r.setMetaPptoCatId(e.getMetaPptoCatId());
        r.setVigenciaInicio(e.getVigenciaInicio());
        r.setVigenciaFin(e.getVigenciaFin());
        r.setEstado(e.getEstado());
        r.setOrigen(e.getOrigen());
        r.setLoteId(e.getLoteId());
        r.setBloqueadoPorPlanilla(e.getBloqueadoPorPlanilla());
        r.setObservacion(e.getObservacion());
        r.setCreadoPor(e.getCreadoPor());
        r.setCreadoEn(e.getCreadoEn());
        r.setModificadoPor(e.getModificadoPor());
        r.setModificadoEn(e.getModificadoEn());
        r.setMotivoAnulacion(e.getMotivoAnulacion());
        // Enriquecer con datos del catálogo
        MetaPptoCat cat = catOpt != null ? catOpt : catRepo.findById(e.getMetaPptoCatId()).orElse(null);
        if (cat != null) {
            r.setMetaCodigo(cat.getMetaCodigo());
            r.setCentroCosto(cat.getCentroCosto());
            r.setCategoriaPresupuestal(cat.getCategoriaPresupuestal());
            r.setProducto(cat.getProducto());
            r.setActividad(cat.getActividad());
            r.setFinalidad(cat.getFinalidad());
        }
        // Enriquecer con nombre/DNI del empleado
        empleadoRepo.findById(e.getEmpleadoId()).ifPresent(emp -> {
            if (emp.getPersonaId() != null) {
                personaRepo.findById(emp.getPersonaId()).ifPresent(p -> {
                    r.setEmpleadoNombre(p.getNombreCompleto());
                    r.setEmpleadoDni(p.getDni());
                });
            }
        });
        return r;
    }

    private void registrarAud(Long empMetaId, Long empleadoId, Integer anioFiscal,
                               String anterior, EmpMetaAnual nuevo, String accion, String motivo, String usuario) {
        MetaPptoAud aud = new MetaPptoAud();
        aud.setEmpMetaId(empMetaId);
        aud.setEmpleadoId(empleadoId);
        aud.setAnioFiscal(anioFiscal);
        aud.setAccion(accion);
        aud.setValorAnterior(anterior);
        aud.setValorNuevo(nuevo != null ? toJson(nuevo) : null);
        aud.setMotivo(motivo);
        aud.setUsuario(usuario);
        aud.setFecha(LocalDateTime.now());
        audRepo.save(aud);
    }

    private EmpMetaTrazabilidadResponse toTrazabilidad(Object[] row) {
        // Layout: [0]id [1]nombres [2]dni [3]anioFiscal [4]metaCodigo
        //         [5]centroCosto [6]categoriaPresupuestal [7]producto [8]actividad [9]finalidad
        //         [10]estado [11]origen [12]bloqueadoPorPlanilla [13]creadoPor [14]creadoEn [15]observacion
        EmpMetaTrazabilidadResponse r = new EmpMetaTrazabilidadResponse();
        r.setId(toLong(row[0]));
        r.setEmpleadoNombre(toStr(row[1]));
        r.setEmpleadoDni(toStr(row[2]));
        r.setAnioFiscal(toInt(row[3]));
        r.setMetaCodigo(toStr(row[4]));
        r.setCentroCosto(toStr(row[5]));
        r.setCategoriaPresupuestal(toStr(row[6]));
        r.setProducto(toStr(row[7]));
        r.setActividad(toStr(row[8]));
        r.setFinalidad(toStr(row[9]));
        r.setEstado(toStr(row[10]));
        r.setOrigen(toStr(row[11]));
        r.setBloqueadoPorPlanilla(toInt(row[12]));
        r.setCreadoPor(toStr(row[13]));
        r.setCreadoEn(row[14] instanceof LocalDateTime ldt ? ldt : null);
        r.setObservacion(toStr(row[15]));
        return r;
    }

    private static Long    toLong(Object v) { return v == null ? null : ((Number) v).longValue(); }
    private static Integer toInt(Object v)  { return v == null ? null : ((Number) v).intValue(); }
    private static String  toStr(Object v)  { return v == null ? null : v.toString(); }

    private String toJson(EmpMetaAnual e) {
        return "{\"id\":" + e.getId() + ",\"metaPptoCatId\":" + e.getMetaPptoCatId()
                + ",\"estado\":\"" + e.getEstado() + "\"}";
    }
}
