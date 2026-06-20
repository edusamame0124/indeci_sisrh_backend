package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MetaPptoLoteDetalleResponse;
import com.indeci.rrhh.dto.MetaPptoLoteDto;
import com.indeci.rrhh.dto.MetaPptoLoteResponse;
import com.indeci.rrhh.dto.MetaResolverDto;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaPptoLoteService {

    private final MetaPptoLoteRepository loteRepo;
    private final MetaPptoLoteDetRepository loteDetRepo;
    private final EmpMetaAnualRepository empMetaRepo;
    private final MetaPptoCatRepository catRepo;
    private final MetaPptoEquivRepository equivRepo;
    private final MetaPptoAudRepository audRepo;
    private final EmpleadoRepository empleadoRepo;
    private final PersonaRepository personaRepo;

    // ========================= CONSULTAS =========================

    @Transactional(readOnly = true)
    public List<MetaPptoLoteResponse> listarPorAnio(Integer anioDestino) {
        return loteRepo.findByAnioDestinoOrderByCreadoEnDesc(anioDestino)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MetaPptoLoteResponse obtener(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<MetaPptoLoteDetalleResponse> listarDetalle(Long loteId) {
        return loteDetRepo.findByLoteIdOrderByEmpleadoId(loteId)
                .stream().map(this::toDetResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MetaPptoLoteDetalleResponse> listarObservados(Long loteId) {
        return loteDetRepo.findObservadosByLote(loteId)
                .stream().map(this::toDetResponse).collect(Collectors.toList());
    }

    // ========================= CREAR LOTE =========================

    @Transactional
    public MetaPptoLoteResponse crearLote(MetaPptoLoteDto dto, String usuario) {
        if (dto.getAnioDestino() == null) throw new NegocioException("El año destino es obligatorio.");
        if (blank(dto.getTipoProceso())) throw new NegocioException("El tipo de proceso es obligatorio.");

        MetaPptoLote lote = new MetaPptoLote();
        lote.setCodigoLote(UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        lote.setAnioOrigen(dto.getAnioOrigen());
        lote.setAnioDestino(dto.getAnioDestino());
        lote.setTipoProceso(dto.getTipoProceso());
        lote.setEstado("CREADO");
        lote.setObservacion(dto.getObservacion());
        lote.setArchivoOrigen(dto.getArchivoOrigen());
        lote.setCreadoPor(usuario);
        lote.setCreadoEn(LocalDateTime.now());
        return toResponse(loteRepo.save(lote));
    }

    // ========================= PROCESO MASIVO =========================

    /**
     * Copia las asignaciones del año origen al año destino,
     * aplicando las equivalencias configuradas.
     * Por cada empleado crea una fila en LOTE_DET con resultado OK u OBSERVADO.
     */
    @Transactional
    public MetaPptoLoteResponse procesarCopiaAnioAnterior(Long loteId, String usuario) {
        MetaPptoLote lote = findOrThrow(loteId);
        if (lote.getAnioOrigen() == null) {
            throw new NegocioException("El lote no tiene año origen configurado.");
        }
        if (!"CREADO".equals(lote.getEstado())) {
            throw new NegocioException("El lote debe estar en estado CREADO para procesarse.");
        }

        lote.setEstado("PROCESANDO");
        loteRepo.save(lote);

        // Eliminar detalle previo si se reprocesa
        loteDetRepo.deleteByLoteId(loteId);

        List<EmpMetaAnual> asignacionesOrigen =
                empMetaRepo.findByAnioFiscalAndEstadoOrderByEmpleadoId(lote.getAnioOrigen(), "PUBLICADO");

        int totalEmpleados = asignacionesOrigen.size();
        int asignados = 0;
        int observados = 0;
        int sinEquiv = 0;
        int duplicados = 0;

        List<MetaPptoLoteDet> detalles = new ArrayList<>();

        for (EmpMetaAnual origen : asignacionesOrigen) {
            MetaPptoLoteDet det = new MetaPptoLoteDet();
            det.setLoteId(loteId);
            det.setEmpleadoId(origen.getEmpleadoId());
            det.setMetaOrigenId(origen.getMetaPptoCatId());
            det.setCreadoPor(usuario);
            det.setCreadoEn(LocalDateTime.now());

            // Verificar si ya existe asignación vigente en destino
            if (empMetaRepo.existsByEmpleadoIdAndAnioFiscalAndEstadoNot(
                    origen.getEmpleadoId(), lote.getAnioDestino(), "ANULADO")) {
                det.setEstadoValidacion("DUPLICADO");
                det.setMensajeValidacion("El empleado ya tiene asignación vigente en el año " + lote.getAnioDestino());
                det.setAccionSugerida("Anule la asignación existente o excluya al empleado del lote.");
                duplicados++;
                detalles.add(det);
                continue;
            }

            // Buscar equivalencia
            Optional<MetaPptoEquiv> equiv = equivRepo.findActivaByOrigenYDestino(
                    origen.getMetaPptoCatId(), lote.getAnioDestino());

            if (equiv.isEmpty()) {
                det.setEstadoValidacion("SIN_EQUIVALENCIA");
                det.setMensajeValidacion("No existe equivalencia configurada para la meta origen en el año " + lote.getAnioDestino());
                det.setAccionSugerida("Configure la equivalencia en la pestaña de Equivalencias.");
                sinEquiv++;
                detalles.add(det);
                continue;
            }

            Long metaDestinoId = equiv.get().getMetaDestinoId();
            MetaPptoCat catDestino = catRepo.findById(metaDestinoId).orElse(null);
            if (catDestino == null || "ANULADO".equals(catDestino.getEstado())) {
                det.setMetaDestinoId(metaDestinoId);
                det.setEstadoValidacion("META_DESTINO_INACTIVA");
                det.setMensajeValidacion("La meta destino no existe o está anulada.");
                det.setAccionSugerida("Actualice la equivalencia con una meta destino activa.");
                observados++;
                detalles.add(det);
                continue;
            }

            // Crear la asignación en el año destino
            EmpMetaAnual nuevaAsig = new EmpMetaAnual();
            nuevaAsig.setEmpleadoId(origen.getEmpleadoId());
            nuevaAsig.setAnioFiscal(lote.getAnioDestino());
            nuevaAsig.setMetaPptoCatId(metaDestinoId);
            nuevaAsig.setVigenciaInicio(LocalDate.of(lote.getAnioDestino(), 1, 1));
            nuevaAsig.setEstado("BORRADOR");
            nuevaAsig.setOrigen("EQUIVALENCIA");
            nuevaAsig.setLoteId(loteId);
            nuevaAsig.setBloqueadoPorPlanilla(0);
            nuevaAsig.setCreadoPor(usuario);
            nuevaAsig.setCreadoEn(LocalDateTime.now());
            EmpMetaAnual saved = empMetaRepo.save(nuevaAsig);

            det.setMetaDestinoId(metaDestinoId);
            det.setEmpMetaAnualId(saved.getId());
            det.setEstadoValidacion("OK");
            asignados++;
            detalles.add(det);
        }

        loteDetRepo.saveAll(detalles);

        // Actualizar contadores
        lote.setTotalEmpleados(totalEmpleados);
        lote.setTotalAsignados(asignados);
        lote.setTotalObservados(observados);
        lote.setTotalSinEquiv(sinEquiv);
        lote.setTotalDuplicados(duplicados);
        lote.setEstado(observados > 0 || sinEquiv > 0 || duplicados > 0 ? "OBSERVADO" : "VALIDADO");
        lote.setFinalizadoEn(LocalDateTime.now());
        return toResponse(loteRepo.save(lote));
    }

    // ========================= RESOLVER EXCEPCIÓN =========================

    @Transactional
    public MetaPptoLoteDetalleResponse resolverExcepcion(Long loteId, MetaResolverDto dto, String usuario) {
        MetaPptoLote lote = findOrThrow(loteId);
        if ("PUBLICADO".equals(lote.getEstado()) || "ANULADO".equals(lote.getEstado())) {
            throw new NegocioException("No se pueden resolver excepciones en un lote " + lote.getEstado());
        }

        MetaPptoLoteDet det = loteDetRepo.findById(dto.getLoteDetId())
                .orElseThrow(() -> new NegocioException("Detalle de lote no encontrado: " + dto.getLoteDetId()));

        MetaPptoCat catDestino = catRepo.findById(dto.getMetaDestinoId())
                .orElseThrow(() -> new NegocioException("Meta destino no encontrada: " + dto.getMetaDestinoId()));
        if ("ANULADO".equals(catDestino.getEstado())) {
            throw new NegocioException("La meta destino seleccionada está anulada.");
        }

        // Crear o actualizar asignación
        if (det.getEmpMetaAnualId() == null) {
            // Verificar duplicado
            if (empMetaRepo.existsByEmpleadoIdAndAnioFiscalAndEstadoNot(
                    det.getEmpleadoId(), lote.getAnioDestino(), "ANULADO")) {
                throw new NegocioException("El empleado ya tiene asignación vigente en el año destino.");
            }
            EmpMetaAnual nuevaAsig = new EmpMetaAnual();
            nuevaAsig.setEmpleadoId(det.getEmpleadoId());
            nuevaAsig.setAnioFiscal(lote.getAnioDestino());
            nuevaAsig.setMetaPptoCatId(dto.getMetaDestinoId());
            nuevaAsig.setVigenciaInicio(LocalDate.of(lote.getAnioDestino(), 1, 1));
            nuevaAsig.setEstado("BORRADOR");
            nuevaAsig.setOrigen("EQUIVALENCIA");
            nuevaAsig.setLoteId(loteId);
            nuevaAsig.setBloqueadoPorPlanilla(0);
            nuevaAsig.setObservacion(dto.getObservacion());
            nuevaAsig.setCreadoPor(usuario);
            nuevaAsig.setCreadoEn(LocalDateTime.now());
            EmpMetaAnual saved = empMetaRepo.save(nuevaAsig);
            det.setEmpMetaAnualId(saved.getId());
        } else {
            empMetaRepo.findById(det.getEmpMetaAnualId()).ifPresent(asig -> {
                asig.setMetaPptoCatId(dto.getMetaDestinoId());
                asig.setObservacion(dto.getObservacion());
                asig.setModificadoPor(usuario);
                asig.setModificadoEn(LocalDateTime.now());
                empMetaRepo.save(asig);
            });
        }

        det.setMetaDestinoId(dto.getMetaDestinoId());
        det.setEstadoValidacion("OK");
        det.setMensajeValidacion("Resuelta manualmente por " + usuario);
        det.setModificadoPor(usuario);
        det.setModificadoEn(LocalDateTime.now());
        loteDetRepo.save(det);

        // Recalcular estado del lote
        recalcularEstadoLote(lote);

        return toDetResponse(det);
    }

    // ========================= PUBLICAR =========================

    @Transactional
    public MetaPptoLoteResponse publicar(Long loteId, String usuario) {
        MetaPptoLote lote = findOrThrow(loteId);
        if (!"VALIDADO".equals(lote.getEstado())) {
            long obs = loteDetRepo.countObservadosByLote(loteId);
            if (obs > 0) {
                throw new NegocioException("El lote tiene " + obs + " excepci" + (obs == 1 ? "ón" : "ones") + " pendientes de resolver.");
            }
        }
        int rows = empMetaRepo.publicarPorLote(loteId);
        lote.setEstado("PUBLICADO");
        lote.setFinalizadoEn(LocalDateTime.now());
        loteRepo.save(lote);

        // Auditoría por lote
        MetaPptoAud aud = new MetaPptoAud();
        aud.setAccion("PUBLICAR_ASIGNACION");
        aud.setValorNuevo("{\"loteId\":" + loteId + ",\"rowsPublicados\":" + rows + "}");
        aud.setUsuario(usuario);
        aud.setFecha(LocalDateTime.now());
        audRepo.save(aud);

        return toResponse(lote);
    }

    // ========================= ANULAR LOTE =========================

    @Transactional
    public void anular(Long loteId, String motivo, String usuario) {
        MetaPptoLote lote = findOrThrow(loteId);
        if ("ANULADO".equals(lote.getEstado())) {
            throw new NegocioException("El lote ya está anulado.");
        }
        if ("PUBLICADO".equals(lote.getEstado())) {
            throw new NegocioException("No se puede anular un lote ya publicado.");
        }
        // Anular asignaciones BORRADOR del lote
        empMetaRepo.findByLoteIdOrderByEmpleadoId(loteId).forEach(asig -> {
            if ("BORRADOR".equals(asig.getEstado())) {
                asig.setEstado("ANULADO");
                asig.setMotivoAnulacion("Anulación del lote " + lote.getCodigoLote());
                asig.setAnuladoPor(usuario);
                asig.setAnuladoEn(LocalDateTime.now());
                empMetaRepo.save(asig);
            }
        });
        lote.setEstado("ANULADO");
        lote.setMotivoAnulacion(motivo);
        lote.setAnuladoPor(usuario);
        lote.setAnuladoEn(LocalDateTime.now());
        loteRepo.save(lote);
    }

    // ========================= HELPERS =========================

    private MetaPptoLote findOrThrow(Long id) {
        return loteRepo.findById(id)
                .orElseThrow(() -> new NegocioException("Lote no encontrado: " + id));
    }

    private void recalcularEstadoLote(MetaPptoLote lote) {
        long obs = loteDetRepo.countObservadosByLote(lote.getId());
        lote.setTotalObservados((int) obs);
        lote.setEstado(obs == 0 ? "VALIDADO" : "OBSERVADO");
        loteRepo.save(lote);
    }

    MetaPptoLoteResponse toResponse(MetaPptoLote e) {
        MetaPptoLoteResponse r = new MetaPptoLoteResponse();
        r.setId(e.getId());
        r.setCodigoLote(e.getCodigoLote());
        r.setAnioOrigen(e.getAnioOrigen());
        r.setAnioDestino(e.getAnioDestino());
        r.setTipoProceso(e.getTipoProceso());
        r.setEstado(e.getEstado());
        r.setTotalEmpleados(e.getTotalEmpleados());
        r.setTotalAsignados(e.getTotalAsignados());
        r.setTotalObservados(e.getTotalObservados());
        r.setTotalErrores(e.getTotalErrores());
        r.setTotalSinEquiv(e.getTotalSinEquiv());
        r.setTotalInactivos(e.getTotalInactivos());
        r.setTotalDuplicados(e.getTotalDuplicados());
        r.setArchivoOrigen(e.getArchivoOrigen());
        r.setObservacion(e.getObservacion());
        r.setCreadoPor(e.getCreadoPor());
        r.setCreadoEn(e.getCreadoEn());
        r.setFinalizadoEn(e.getFinalizadoEn());
        r.setMotivoAnulacion(e.getMotivoAnulacion());
        return r;
    }

    MetaPptoLoteDetalleResponse toDetResponse(MetaPptoLoteDet d) {
        MetaPptoLoteDetalleResponse r = new MetaPptoLoteDetalleResponse();
        r.setId(d.getId());
        r.setLoteId(d.getLoteId());
        r.setEmpleadoId(d.getEmpleadoId());
        r.setMetaOrigenId(d.getMetaOrigenId());
        r.setMetaDestinoId(d.getMetaDestinoId());
        r.setEmpMetaAnualId(d.getEmpMetaAnualId());
        r.setEstadoValidacion(d.getEstadoValidacion());
        r.setMensajeValidacion(d.getMensajeValidacion());
        r.setAccionSugerida(d.getAccionSugerida());
        // Enriquecer nombre/DNI del empleado
        empleadoRepo.findById(d.getEmpleadoId()).ifPresent(emp -> {
            if (emp.getPersonaId() != null) {
                personaRepo.findById(emp.getPersonaId()).ifPresent(p -> {
                    r.setEmpleadoNombre(p.getNombreCompleto());
                    r.setEmpleadoDni(p.getDni());
                });
            }
        });
        // Enriquecer código meta origen
        if (d.getMetaOrigenId() != null) {
            catRepo.findById(d.getMetaOrigenId()).ifPresent(c -> r.setMetaOrigenCodigo(c.getMetaCodigo()));
        }
        // Enriquecer código meta destino
        if (d.getMetaDestinoId() != null) {
            catRepo.findById(d.getMetaDestinoId()).ifPresent(c -> {
                r.setMetaDestinoCodigo(c.getMetaCodigo());
                r.setMetaDestinoDescripcion(c.getCentroCosto() + " / " + c.getActividad());
            });
        }
        return r;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
