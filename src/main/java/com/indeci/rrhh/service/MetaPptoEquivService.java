package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.DeteccionEquivRequestDto;
import com.indeci.rrhh.dto.DeteccionEquivResultDto;
import com.indeci.rrhh.dto.MetaPptoEquivDto;
import com.indeci.rrhh.dto.MetaPptoEquivResponse;
import com.indeci.rrhh.entity.MetaPptoCat;
import com.indeci.rrhh.entity.MetaPptoEquiv;
import com.indeci.rrhh.repository.EmpMetaAnualRepository;
import com.indeci.rrhh.repository.MetaPptoCatRepository;
import com.indeci.rrhh.repository.MetaPptoEquivRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaPptoEquivService {

    private final MetaPptoEquivRepository equivRepo;
    private final MetaPptoCatRepository catRepo;
    private final EmpMetaAnualRepository empMetaRepo;

    // ========================= CONSULTAS =========================

    @Transactional(readOnly = true)
    public List<MetaPptoEquivResponse> listarPorAnios(Integer anioOrigen, Integer anioDestino) {
        return equivRepo.findByAnioOrigenAndAnioDestinoOrderByMetaOrigenId(anioOrigen, anioDestino)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MetaPptoEquivResponse> listarActivasPorAnios(Integer anioOrigen, Integer anioDestino) {
        return equivRepo.findActivasByAnios(anioOrigen, anioDestino)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MetaPptoEquivResponse obtener(Long id) {
        return toResponse(findOrThrow(id));
    }

    // ========================= DETECCIÓN AUTOMÁTICA =========================

    /**
     * Compara la estructura (centroCosto + categoriaPresupuestal + producto + actividad + finalidad)
     * de cada meta con asignaciones en anioOrigen contra el catálogo de anioDestino.
     * Crea equivalencias AUTOMATICO cuando hay coincidencia exacta de 1 sola meta.
     */
    @Transactional
    public List<DeteccionEquivResultDto> detectarEquivalenciasAuto(DeteccionEquivRequestDto req, String usuario) {
        if (req.getAnioOrigen() == null || req.getAnioDestino() == null) {
            throw new NegocioException("Año origen y destino son obligatorios.");
        }
        if (req.getAnioDestino() <= req.getAnioOrigen()) {
            throw new NegocioException("El año destino debe ser mayor que el año origen.");
        }

        List<Long> metaIdsOrigen = empMetaRepo.findMetaIdsConAsignaciones(req.getAnioOrigen());
        List<DeteccionEquivResultDto> resultados = new ArrayList<>();

        for (Long metaOrigenId : metaIdsOrigen) {
            MetaPptoCat origen = catRepo.findById(metaOrigenId).orElse(null);
            if (origen == null) continue;

            long trabajadores = empMetaRepo.countByMetaPptoCatIdAndAnioFiscal(metaOrigenId, req.getAnioOrigen());

            List<MetaPptoCat> coincidencias = catRepo.findByEstructura(
                    req.getAnioDestino(),
                    origen.getCentroCosto(),
                    origen.getCategoriaPresupuestal(),
                    origen.getProducto(),
                    origen.getActividad(),
                    origen.getFinalidad()
            );

            DeteccionEquivResultDto r = new DeteccionEquivResultDto();
            r.setAnioOrigen(req.getAnioOrigen());
            r.setMetaOrigenId(metaOrigenId);
            r.setMetaOrigenCodigo(origen.getMetaCodigo());
            r.setCentroCosto(origen.getCentroCosto());
            r.setCategoriaPresupuestal(origen.getCategoriaPresupuestal());
            r.setProducto(origen.getProducto());
            r.setActividad(origen.getActividad());
            r.setFinalidad(origen.getFinalidad());
            r.setTrabajadoresAsignados(trabajadores);

            if (coincidencias.size() == 1) {
                MetaPptoCat destino = coincidencias.get(0);
                r.setMetaDestinoId(destino.getId());
                r.setMetaDestinoCodigo(destino.getMetaCodigo());

                if ("ANULADO".equals(destino.getEstado()) || "CERRADO".equals(destino.getEstado())) {
                    r.setEstadoDeteccion("OBSERVADO");
                    r.setObservacion("La meta destino encontrada está en estado " + destino.getEstado() + " y no es asignable.");
                } else {
                    r.setEstadoDeteccion("OK_AUTOMATICO");
                    // Crear equivalencia si no existe una activa para este par
                    Optional<MetaPptoEquiv> existente = equivRepo.findActivaByOrigenYDestino(metaOrigenId, req.getAnioDestino());
                    if (existente.isEmpty()) {
                        MetaPptoEquiv equiv = new MetaPptoEquiv();
                        equiv.setAnioOrigen(req.getAnioOrigen());
                        equiv.setMetaOrigenId(metaOrigenId);
                        equiv.setAnioDestino(req.getAnioDestino());
                        equiv.setMetaDestinoId(destino.getId());
                        equiv.setEstado("BORRADOR");
                        equiv.setActivo(1);
                        equiv.setTipoOrigen("AUTOMATICO");
                        equiv.setObservacion("Detectado automáticamente por coincidencia estructural.");
                        equiv.setCreadoPor(usuario);
                        equiv.setCreadoEn(LocalDateTime.now());
                        r.setEquivalenciaId(equivRepo.save(equiv).getId());
                    } else {
                        r.setEquivalenciaId(existente.get().getId());
                        r.setObservacion("Equivalencia ya existía.");
                    }
                }
            } else if (coincidencias.isEmpty()) {
                r.setEstadoDeteccion("SIN_COINCIDENCIA");
                r.setObservacion("No se encontró ninguna meta con la misma estructura en el año " + req.getAnioDestino() + ".");
            } else {
                r.setEstadoDeteccion("COINCIDENCIA_MULTIPLE");
                r.setObservacion("Se encontraron " + coincidencias.size() + " metas con la misma estructura. Requiere selección manual.");
            }

            resultados.add(r);
        }

        return resultados;
    }

    // ========================= CRUD =========================

    @Transactional
    public MetaPptoEquivResponse crear(MetaPptoEquivDto dto, String usuario) {
        validar(dto);
        if (equivRepo.existsByMetaOrigenIdAndAnioDestinoAndActivoAndEstadoNot(
                dto.getMetaOrigenId(), dto.getAnioDestino(), 1, "ANULADO")) {
            throw new NegocioException("Ya existe una equivalencia activa para esa meta origen en el año destino " + dto.getAnioDestino());
        }
        MetaPptoEquiv equiv = new MetaPptoEquiv();
        equiv.setAnioOrigen(dto.getAnioOrigen());
        equiv.setMetaOrigenId(dto.getMetaOrigenId());
        equiv.setAnioDestino(dto.getAnioDestino());
        equiv.setMetaDestinoId(dto.getMetaDestinoId());
        equiv.setObservacion(dto.getObservacion());
        equiv.setEstado("BORRADOR");
        equiv.setActivo(1);
        equiv.setTipoOrigen("MANUAL");
        equiv.setCreadoPor(usuario);
        equiv.setCreadoEn(LocalDateTime.now());
        return toResponse(equivRepo.save(equiv));
    }

    @Transactional
    public MetaPptoEquivResponse editar(Long id, MetaPptoEquivDto dto, String usuario) {
        MetaPptoEquiv equiv = findOrThrow(id);
        if ("PUBLICADO".equals(equiv.getEstado())) {
            throw new NegocioException("No se puede editar una equivalencia PUBLICADA.");
        }
        equiv.setMetaDestinoId(dto.getMetaDestinoId());
        equiv.setObservacion(dto.getObservacion());
        equiv.setTipoOrigen("MANUAL");
        equiv.setModificadoPor(usuario);
        equiv.setModificadoEn(LocalDateTime.now());
        return toResponse(equivRepo.save(equiv));
    }

    @Transactional
    public void anular(Long id, String motivo, String usuario) {
        MetaPptoEquiv equiv = findOrThrow(id);
        if ("ANULADO".equals(equiv.getEstado())) {
            throw new NegocioException("La equivalencia ya está anulada.");
        }
        equiv.setEstado("ANULADO");
        equiv.setActivo(0);
        equiv.setMotivoAnulacion(motivo);
        equiv.setAnuladoPor(usuario);
        equiv.setAnuladoEn(LocalDateTime.now());
        equivRepo.save(equiv);
    }

    // ========================= HELPERS =========================

    private MetaPptoEquiv findOrThrow(Long id) {
        return equivRepo.findById(id)
                .orElseThrow(() -> new NegocioException("Equivalencia no encontrada: " + id));
    }

    private void validar(MetaPptoEquivDto dto) {
        if (dto.getMetaOrigenId() == null) throw new NegocioException("La meta origen es obligatoria.");
        if (dto.getMetaDestinoId() == null) throw new NegocioException("La meta destino es obligatoria.");
        if (dto.getAnioDestino() == null) throw new NegocioException("El año destino es obligatorio.");
        if (dto.getMetaOrigenId().equals(dto.getMetaDestinoId()) &&
            (dto.getAnioOrigen() != null && dto.getAnioOrigen().equals(dto.getAnioDestino()))) {
            throw new NegocioException("La meta origen y destino no pueden ser la misma dentro del mismo año.");
        }
    }

    MetaPptoEquivResponse toResponse(MetaPptoEquiv e) {
        MetaPptoEquivResponse r = new MetaPptoEquivResponse();
        r.setId(e.getId());
        r.setAnioOrigen(e.getAnioOrigen());
        r.setMetaOrigenId(e.getMetaOrigenId());
        r.setAnioDestino(e.getAnioDestino());
        r.setMetaDestinoId(e.getMetaDestinoId());
        r.setEstado(e.getEstado());
        r.setActivo(e.getActivo());
        r.setObservacion(e.getObservacion());
        r.setCreadoPor(e.getCreadoPor());
        r.setCreadoEn(e.getCreadoEn());
        r.setMotivoAnulacion(e.getMotivoAnulacion());
        catRepo.findById(e.getMetaOrigenId()).ifPresent(cat -> {
            r.setMetaOrigenCodigo(cat.getMetaCodigo());
            r.setMetaOrigenDescripcion(descripcion(cat));
        });
        catRepo.findById(e.getMetaDestinoId()).ifPresent(cat -> {
            r.setMetaDestinoCodigo(cat.getMetaCodigo());
            r.setMetaDestinoDescripcion(descripcion(cat));
        });
        return r;
    }

    private String descripcion(MetaPptoCat c) {
        return c.getCentroCosto() + " / " + c.getCategoriaPresupuestal() + " / " + c.getActividad();
    }
}
