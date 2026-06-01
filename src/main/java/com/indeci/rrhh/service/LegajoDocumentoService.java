package com.indeci.rrhh.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.LegajoDocumentoDto;
import com.indeci.rrhh.dto.LegajoDocumentoResponseDto;
import com.indeci.rrhh.entity.LegajoCategoria;
import com.indeci.rrhh.entity.LegajoDocumento;
import com.indeci.rrhh.entity.LegajoSubcategoria;
import com.indeci.rrhh.repository.LegajoCategoriaRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;
import com.indeci.rrhh.repository.LegajoSubcategoriaRepository;

import lombok.RequiredArgsConstructor;

/**
 * F2.6 — Gestión del legajo documental del empleado.
 *
 * <p>Sube el archivo binario al FTP institucional ({@link FtpService}) y
 * registra la metadata en {@code INDECI_LEGAJO_DOCUMENTO}. Devuelve el id
 * del registro, que otros módulos (eventos del período, asistencia, etc.)
 * pueden referenciar como sustento documental.</p>
 *
 * <p>Convención de path FTP: {@code <basePath>/empleado-<id>/<categoria>/<file>}.
 * El nombre del archivo en FTP es {@code <timestamp>_<original>} para evitar
 * colisiones cuando se sube el mismo nombre dos veces.</p>
 */
@Service
@RequiredArgsConstructor
public class LegajoDocumentoService {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final String ORIGEN_DEFAULT = "MANUAL";

    private final LegajoDocumentoRepository repository;
    private final LegajoCategoriaRepository categoriaRepository;
    private final LegajoSubcategoriaRepository subcategoriaRepository;
    private final FtpService ftpService;
    private final AuditoriaContext auditoriaContext;

    // =================== LISTAR ===================

    public List<LegajoDocumentoResponseDto> listarPorEmpleado(Long empleadoId) {
        if (empleadoId == null) {
            throw new NegocioException("empleadoId requerido");
        }
        return repository
                .findByEmpleadoIdAndActivoOrderByCreatedAtDesc(empleadoId, 1)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<LegajoDocumentoResponseDto> listarPorEmpleadoYCategoria(
            Long empleadoId, Long categoriaId) {
        if (empleadoId == null || categoriaId == null) {
            throw new NegocioException("empleadoId y categoriaId requeridos");
        }
        return repository
                .findByEmpleadoIdAndCategoriaIdAndActivoOrderByCreatedAtDesc(
                        empleadoId, categoriaId, 1)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public LegajoDocumentoResponseDto obtener(Long id) {
        return toResponse(buscar(id));
    }

    // =================== SUBIR (CREAR) ===================

    @Auditable(accion = "SUBIR_DOCUMENTO_LEGAJO")
    public LegajoDocumentoResponseDto subir(
            LegajoDocumentoDto dto, MultipartFile file) {

        LegajoCategoria categoria = validar(dto, file);
        LegajoSubcategoria subcategoria = validarSubcategoria(dto, categoria);

        String original = sanitizar(file.getOriginalFilename());
        String extension = extensionDe(original);
        String nombreFtp = LocalDateTime.now().format(TS) + "_" + original;
        String carpeta = "empleado-" + dto.getEmpleadoId()
                + "/" + slug(categoria.getNombre());

        String ruta = ftpService.subirArchivo(file, carpeta, nombreFtp);

        LegajoDocumento entity = new LegajoDocumento();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setCategoriaId(categoria.getId());
        entity.setSubcategoriaId(subcategoria != null ? subcategoria.getId() : null);
        entity.setNombreDocumento(nombreLegible(dto, original));
        entity.setNombreArchivo(nombreFtp);
        entity.setRutaArchivo(ruta);
        entity.setExtension(extension);
        entity.setPesoArchivo(file.getSize());
        entity.setFechaDocumento(dto.getFechaDocumento());
        entity.setObservacion(dto.getObservacion());
        entity.setOrigen(dto.getOrigen() != null ? dto.getOrigen() : ORIGEN_DEFAULT);
        entity.setReferenciaId(dto.getReferenciaId());
        entity.setVersionDoc(1);
        entity.setActivo(1);
        entity.setCreatedAt(LocalDateTime.now());

        LegajoDocumento guardado = repository.save(entity);
        auditoriaContext.setDetalle(
                "Documento legajo subido — empleado " + dto.getEmpleadoId()
                + ", categoría " + categoria.getNombre()
                + ", archivo " + nombreFtp);

        return toResponse(guardado);
    }

    // =================== ELIMINAR ===================

    @Auditable(accion = "ELIMINAR_DOCUMENTO_LEGAJO")
    public void eliminar(Long id) {
        LegajoDocumento entity = buscar(id);
        entity.setActivo(0);
        repository.save(entity);
        auditoriaContext.setDetalle("Documento legajo desactivado ID: " + id);
    }

    // =================== VALIDACIONES ===================

    private LegajoCategoria validar(LegajoDocumentoDto dto, MultipartFile file) {
        if (dto == null) {
            throw new NegocioException("Datos del documento requeridos");
        }
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("empleadoId requerido");
        }
        if (dto.getCategoriaId() == null) {
            throw new NegocioException("categoriaId requerido");
        }
        if (file == null || file.isEmpty()) {
            throw new NegocioException("Archivo vacío o ausente");
        }
        if (file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()) {
            throw new NegocioException("El archivo no tiene nombre");
        }

        return categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new NegocioException(
                        "Categoría de legajo no existe: " + dto.getCategoriaId()));
    }

    private LegajoSubcategoria validarSubcategoria(
            LegajoDocumentoDto dto, LegajoCategoria categoria) {
        if (dto.getSubcategoriaId() == null) {
            return null;
        }
        LegajoSubcategoria sub = subcategoriaRepository
                .findById(dto.getSubcategoriaId())
                .orElseThrow(() -> new NegocioException(
                        "Subcategoría de legajo no existe: " + dto.getSubcategoriaId()));
        if (!categoria.getId().equals(sub.getCategoriaId())) {
            throw new NegocioException(
                    "La subcategoría " + sub.getId()
                    + " no pertenece a la categoría " + categoria.getId());
        }
        return sub;
    }

    // =================== HELPERS ===================

    private LegajoDocumento buscar(Long id) {
        if (id == null) {
            throw new NegocioException("ID de documento requerido");
        }
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException(
                        "Documento legajo no encontrado: " + id));
    }

    /**
     * Reemplaza caracteres no seguros para nombre de archivo / path FTP.
     * Conserva letras, dígitos, punto, guión, underscore.
     */
    static String sanitizar(String s) {
        if (s == null) return "archivo";
        String limpio = s.replaceAll("[^A-Za-z0-9._\\-]", "_");
        if (limpio.isBlank()) return "archivo";
        return limpio;
    }

    /** Slug simple para nombres de categoría — minúsculas + sin espacios. */
    static String slug(String nombre) {
        if (nombre == null) return "otros";
        return nombre.toLowerCase()
                .replaceAll("[áàä]", "a").replaceAll("[éèë]", "e")
                .replaceAll("[íìï]", "i").replaceAll("[óòö]", "o")
                .replaceAll("[úùü]", "u").replaceAll("[ñ]", "n")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    static String extensionDe(String nombre) {
        if (nombre == null) return null;
        int idx = nombre.lastIndexOf('.');
        if (idx < 0 || idx == nombre.length() - 1) return null;
        String ext = nombre.substring(idx + 1).toLowerCase();
        return ext.length() <= 10 ? ext : null;
    }

    private String nombreLegible(LegajoDocumentoDto dto, String fallback) {
        if (dto.getNombreDocumento() != null
                && !dto.getNombreDocumento().isBlank()) {
            return dto.getNombreDocumento();
        }
        return fallback;
    }

    private LegajoDocumentoResponseDto toResponse(LegajoDocumento e) {
        LegajoDocumentoResponseDto dto = new LegajoDocumentoResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setCategoriaId(e.getCategoriaId());
        dto.setSubcategoriaId(e.getSubcategoriaId());
        dto.setNombreDocumento(e.getNombreDocumento());
        dto.setNombreArchivo(e.getNombreArchivo());
        dto.setRutaArchivo(e.getRutaArchivo());
        dto.setExtension(e.getExtension());
        dto.setPesoArchivo(e.getPesoArchivo());
        dto.setFechaDocumento(e.getFechaDocumento());
        dto.setObservacion(e.getObservacion());
        dto.setOrigen(e.getOrigen());
        dto.setReferenciaId(e.getReferenciaId());
        dto.setVersionDoc(e.getVersionDoc());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setCreatedBy(e.getCreatedBy());

        if (e.getCategoriaId() != null) {
            Optional<LegajoCategoria> cat = categoriaRepository.findById(e.getCategoriaId());
            cat.ifPresent(c -> dto.setCategoriaNombre(c.getNombre()));
        }
        if (e.getSubcategoriaId() != null) {
            Optional<LegajoSubcategoria> sub = subcategoriaRepository.findById(e.getSubcategoriaId());
            sub.ifPresent(s -> dto.setSubcategoriaNombre(s.getNombre()));
        }
        return dto;
    }
}
