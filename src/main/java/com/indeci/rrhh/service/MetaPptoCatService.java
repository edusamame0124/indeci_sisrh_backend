package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.CambioEstadoMasivoDto;
import com.indeci.rrhh.dto.CambioEstadoMasivoResultDto;
import com.indeci.rrhh.dto.MetaPptoCatDto;
import com.indeci.rrhh.dto.MetaPptoCatImportDto;
import com.indeci.rrhh.dto.MetaPptoCatResponse;
import com.indeci.rrhh.entity.MetaPptoCat;
import com.indeci.rrhh.entity.MetaPptoAud;
import com.indeci.rrhh.repository.MetaPptoCatRepository;
import com.indeci.rrhh.repository.MetaPptoAudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaPptoCatService {

    private final MetaPptoCatRepository catRepo;
    private final MetaPptoAudRepository audRepo;

    private static final Map<String, List<String>> TRANSICIONES_VALIDAS = Map.of(
            "VALIDADO",  List.of("BORRADOR"),
            "PUBLICADO", List.of("BORRADOR", "VALIDADO"),
            "CERRADO",   List.of("PUBLICADO"),
            "ANULADO",   List.of("BORRADOR", "VALIDADO")
    );

    // ========================= CONSULTAS =========================

    @Transactional(readOnly = true)
    public List<MetaPptoCatResponse> listarPorAnio(Integer anioFiscal) {
        return catRepo.findActivasByAnio(anioFiscal)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MetaPptoCatResponse obtener(Long id) {
        return toResponse(findOrThrow(id));
    }

    // ========================= CRUD =========================

    @Transactional
    public MetaPptoCatResponse crear(MetaPptoCatDto dto, String usuario) {
        validarCamposObligatorios(dto);
        MetaPptoCat cat = fromDto(dto);
        cat.setCreadoPor(usuario);
        cat.setCreadoEn(LocalDateTime.now());
        MetaPptoCat saved = catRepo.save(cat);
        registrarAud(null, null, null, saved, "IMPORTAR_CATALOGO", null, usuario);
        return toResponse(saved);
    }

    @Transactional
    public MetaPptoCatResponse editar(Long id, MetaPptoCatDto dto, String usuario) {
        MetaPptoCat cat = findOrThrow(id);
        if ("PUBLICADO".equals(cat.getEstado()) || "CERRADO".equals(cat.getEstado())) {
            throw new NegocioException("No se puede editar una meta en estado " + cat.getEstado());
        }
        String anterior = toJson(cat);
        cat.setMetaCodigo(dto.getMetaCodigo());
        cat.setCentroCosto(dto.getCentroCosto());
        cat.setCategoriaPresupuestal(dto.getCategoriaPresupuestal());
        cat.setProducto(dto.getProducto());
        cat.setActividad(dto.getActividad());
        cat.setFinalidad(dto.getFinalidad());
        cat.setFuente(dto.getFuente());
        cat.setObservacion(dto.getObservacion());
        cat.setModificadoPor(usuario);
        cat.setModificadoEn(LocalDateTime.now());
        MetaPptoCat saved = catRepo.save(cat);
        registrarAud(id, null, anterior, saved, "EDITAR_META", null, usuario);
        return toResponse(saved);
    }

    @Transactional
    public void anular(Long id, String motivo, String usuario) {
        MetaPptoCat cat = findOrThrow(id);
        if ("ANULADO".equals(cat.getEstado())) {
            throw new NegocioException("La meta ya está anulada.");
        }
        if ("PUBLICADO".equals(cat.getEstado())) {
            throw new NegocioException("No se puede anular una meta PUBLICADA. Cierre el catálogo primero.");
        }
        String anterior = toJson(cat);
        cat.setEstado("ANULADO");
        cat.setActivo(0);
        cat.setMotivoAnulacion(motivo);
        cat.setAnuladoPor(usuario);
        cat.setAnuladoEn(LocalDateTime.now());
        catRepo.save(cat);
        registrarAud(id, null, anterior, null, "ANULAR_META", motivo, usuario);
    }

    // ========================= CAMBIO MASIVO DE ESTADO (B3) =========================

    @Transactional
    public CambioEstadoMasivoResultDto cambiarEstadoMasivo(CambioEstadoMasivoDto dto, String usuario) {
        if (dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new NegocioException("Debe indicar al menos un ID.");
        }
        if (dto.getNuevoEstado() == null || !TRANSICIONES_VALIDAS.containsKey(dto.getNuevoEstado())) {
            throw new NegocioException("Estado destino inválido: " + dto.getNuevoEstado());
        }

        List<String> estadosOrigen = TRANSICIONES_VALIDAS.get(dto.getNuevoEstado());
        int exitosos = 0;
        int omitidos = 0;
        List<String> errores = new ArrayList<>();

        for (Long id : dto.getIds()) {
            MetaPptoCat cat = catRepo.findById(id).orElse(null);
            if (cat == null) {
                errores.add("Meta ID " + id + " no encontrada.");
                omitidos++;
                continue;
            }
            if (!estadosOrigen.contains(cat.getEstado())) {
                errores.add("Meta " + cat.getMetaCodigo() + " (estado actual: " + cat.getEstado()
                        + ") no puede pasar a " + dto.getNuevoEstado() + ".");
                omitidos++;
                continue;
            }
            String anterior = toJson(cat);
            cat.setEstado(dto.getNuevoEstado());
            if ("ANULADO".equals(dto.getNuevoEstado())) {
                cat.setActivo(0);
                cat.setMotivoAnulacion(dto.getMotivo());
                cat.setAnuladoPor(usuario);
                cat.setAnuladoEn(LocalDateTime.now());
            } else {
                cat.setModificadoPor(usuario);
                cat.setModificadoEn(LocalDateTime.now());
            }
            catRepo.save(cat);
            registrarAud(id, null, anterior, cat, "EDITAR_META", dto.getMotivo(), usuario);
            exitosos++;
        }

        return new CambioEstadoMasivoResultDto(exitosos, omitidos, errores);
    }

    // ========================= IMPORTACIÓN MASIVA =========================

    @Transactional
    public List<MetaPptoCatResponse> importar(MetaPptoCatImportDto importDto, String usuario) {
        if (importDto.getFilas() == null || importDto.getFilas().isEmpty()) {
            throw new NegocioException("No se recibieron filas para importar.");
        }
        List<MetaPptoCatResponse> resultado = new ArrayList<>();
        for (MetaPptoCatDto dto : importDto.getFilas()) {
            dto.setAnioFiscal(importDto.getAnioFiscal());
            validarCamposObligatorios(dto);
            MetaPptoCat cat = fromDto(dto);
            cat.setCreadoPor(usuario);
            cat.setCreadoEn(LocalDateTime.now());
            MetaPptoCat saved = catRepo.save(cat);
            registrarAud(null, null, null, saved, "IMPORTAR_CATALOGO", null, usuario);
            resultado.add(toResponse(saved));
        }
        return resultado;
    }

    // ========================= PUBLICAR =========================

    @Transactional
    public void publicarCatalogo(Integer anioFiscal, String usuario) {
        List<MetaPptoCat> metas = catRepo.findByAnioFiscalAndEstadoOrderByMetaCodigo(anioFiscal, "BORRADOR");
        if (metas.isEmpty()) {
            throw new NegocioException("No hay metas en estado BORRADOR para publicar en el año " + anioFiscal);
        }
        for (MetaPptoCat m : metas) {
            m.setEstado("PUBLICADO");
            m.setModificadoPor(usuario);
            m.setModificadoEn(LocalDateTime.now());
        }
        catRepo.saveAll(metas);
    }

    // ========================= HELPERS =========================

    private MetaPptoCat findOrThrow(Long id) {
        return catRepo.findById(id)
                .orElseThrow(() -> new NegocioException("Meta presupuestal no encontrada: " + id));
    }

    private void validarCamposObligatorios(MetaPptoCatDto dto) {
        if (dto.getAnioFiscal() == null) throw new NegocioException("El año fiscal es obligatorio.");
        if (blank(dto.getMetaCodigo())) throw new NegocioException("El código de meta es obligatorio.");
        if (blank(dto.getCentroCosto())) throw new NegocioException("El centro de costo es obligatorio.");
        if (blank(dto.getCategoriaPresupuestal())) throw new NegocioException("La categoría presupuestal es obligatoria.");
        if (blank(dto.getProducto())) throw new NegocioException("El producto es obligatorio.");
        if (blank(dto.getActividad())) throw new NegocioException("La actividad es obligatoria.");
        if (blank(dto.getFinalidad())) throw new NegocioException("La finalidad es obligatoria.");
    }

    private MetaPptoCat fromDto(MetaPptoCatDto dto) {
        MetaPptoCat c = new MetaPptoCat();
        c.setAnioFiscal(dto.getAnioFiscal());
        c.setMetaCodigo(trim(dto.getMetaCodigo()));
        c.setCentroCosto(trim(dto.getCentroCosto()));
        c.setCategoriaPresupuestal(trim(dto.getCategoriaPresupuestal()));
        c.setProducto(trim(dto.getProducto()));
        c.setActividad(trim(dto.getActividad()));
        c.setFinalidad(trim(dto.getFinalidad()));
        c.setFuente(dto.getFuente());
        c.setObservacion(dto.getObservacion());
        c.setEstado("BORRADOR");
        c.setActivo(1);
        return c;
    }

    MetaPptoCatResponse toResponse(MetaPptoCat e) {
        MetaPptoCatResponse r = new MetaPptoCatResponse();
        r.setId(e.getId());
        r.setAnioFiscal(e.getAnioFiscal());
        r.setMetaCodigo(e.getMetaCodigo());
        r.setCentroCosto(e.getCentroCosto());
        r.setCategoriaPresupuestal(e.getCategoriaPresupuestal());
        r.setProducto(e.getProducto());
        r.setActividad(e.getActividad());
        r.setFinalidad(e.getFinalidad());
        r.setMetaHash(e.getMetaHash());
        r.setEstado(e.getEstado());
        r.setActivo(e.getActivo());
        r.setFuente(e.getFuente());
        r.setObservacion(e.getObservacion());
        r.setCreadoPor(e.getCreadoPor());
        r.setCreadoEn(e.getCreadoEn());
        r.setModificadoPor(e.getModificadoPor());
        r.setModificadoEn(e.getModificadoEn());
        r.setMotivoAnulacion(e.getMotivoAnulacion());
        return r;
    }

    private void registrarAud(Long empMetaId, Long empleadoId, String anterior, MetaPptoCat nuevo, String accion, String motivo, String usuario) {
        MetaPptoAud aud = new MetaPptoAud();
        aud.setEmpMetaId(empMetaId);
        aud.setEmpleadoId(empleadoId);
        aud.setAccion(accion);
        aud.setValorAnterior(anterior);
        aud.setValorNuevo(nuevo != null ? toJson(nuevo) : null);
        aud.setMotivo(motivo);
        aud.setUsuario(usuario);
        aud.setFecha(LocalDateTime.now());
        audRepo.save(aud);
    }

    private String toJson(MetaPptoCat c) {
        return "{\"id\":" + c.getId() + ",\"metaCodigo\":\"" + c.getMetaCodigo()
                + "\",\"estado\":\"" + c.getEstado() + "\"}";
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String trim(String s) { return s == null ? null : s.trim(); }
}
