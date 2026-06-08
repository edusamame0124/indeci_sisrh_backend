package com.indeci.rrhh.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.LegajoArchivoDescargaDto;
import com.indeci.rrhh.dto.LegajoDocumentoDto;
import com.indeci.rrhh.dto.LegajoDocumentoResponseDto;
import com.indeci.rrhh.entity.LegajoCategoria;
import com.indeci.rrhh.entity.LegajoSubcategoria;
import com.indeci.rrhh.repository.LegajoCategoriaRepository;
import com.indeci.rrhh.repository.LegajoSubcategoriaRepository;
import com.indeci.rrhh.service.LegajoDocumentoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F2.6 — Endpoint REST del legajo documental del empleado.
 *
 * <p>Rutas:</p>
 * <ul>
 *   <li>{@code GET    /api/rrhh/legajo/categorias}                    — catálogo categorías</li>
 *   <li>{@code GET    /api/rrhh/legajo/categorias/{id}/subcategorias} — subcategorías</li>
 *   <li>{@code GET    /api/rrhh/legajo/empleado/{empleadoId}}          — listar por empleado</li>
 *   <li>{@code GET    /api/rrhh/legajo/empleado/{empleadoId}/categoria/{catId}} — por categoría</li>
 *   <li>{@code GET    /api/rrhh/legajo/{id}}                           — obtener uno</li>
 *   <li>{@code GET    /api/rrhh/legajo/{id}/download}                  — descarga binario</li>
 *   <li>{@code POST   /api/rrhh/legajo/upload}                         — sube archivo (multipart)</li>
 *   <li>{@code DELETE /api/rrhh/legajo/{id}}                           — baja lógica</li>
 * </ul>
 *
 * <p>El upload recibe {@code multipart/form-data}:</p>
 * <pre>
 *   - file:             MultipartFile (binario)
 *   - empleadoId:       Long
 *   - categoriaId:      Long
 *   - subcategoriaId:   Long (opcional)
 *   - nombreDocumento:  String (opcional, default = nombre original)
 *   - fechaDocumento:   YYYY-MM-DD (opcional)
 *   - observacion:      String (opcional)
 *   - origen:           String (opcional, default "MANUAL")
 *   - referenciaId:     Long (opcional, ej. id de EmpleadoEvento)
 * </pre>
 *
 * <p>El binario va a {@code FtpService}; la metadata a
 * {@code INDECI_LEGAJO_DOCUMENTO}. Devuelve el id del documento para que el
 * llamador lo enlace como {@code sustentoLegajoDocId} (eventos, asistencia,
 * etc.).</p>
 */
@RestController
@RequestMapping("/api/rrhh/legajo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class LegajoDocumentoController {

    private final LegajoDocumentoService service;
    private final LegajoCategoriaRepository categoriaRepository;
    private final LegajoSubcategoriaRepository subcategoriaRepository;

    @GetMapping("/categorias")
    public ApiResponse<List<LegajoCategoria>> categorias() {
        return new ApiResponse<>(
                "OK",
                "Categorías del legajo",
                categoriaRepository.findByActivoOrderByOrdenVisualAsc(1));
    }

    @GetMapping("/categorias/{categoriaId}/subcategorias")
    public ApiResponse<List<LegajoSubcategoria>> subcategorias(
            @PathVariable Long categoriaId) {
        return new ApiResponse<>(
                "OK",
                "Subcategorías de la categoría",
                subcategoriaRepository
                        .findByCategoriaIdAndActivoOrderByOrdenVisualAsc(categoriaId, 1));
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<LegajoDocumentoResponseDto>> listar(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>(
                "OK",
                "Documentos del empleado",
                service.listarPorEmpleado(empleadoId));
    }

    @GetMapping("/empleado/{empleadoId}/categoria/{categoriaId}")
    public ApiResponse<List<LegajoDocumentoResponseDto>> listarPorCategoria(
            @PathVariable Long empleadoId,
            @PathVariable Long categoriaId) {
        return new ApiResponse<>(
                "OK",
                "Documentos por categoría",
                service.listarPorEmpleadoYCategoria(empleadoId, categoriaId));
    }

    @GetMapping("/{id}")
    public ApiResponse<LegajoDocumentoResponseDto> obtener(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Documento", service.obtener(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        LegajoArchivoDescargaDto archivo = service.descargar(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(archivo.mediaType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(archivo.nombreArchivo())
                .build());

        return ResponseEntity.ok().headers(headers).body(archivo.contenido());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<LegajoDocumentoResponseDto> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long empleadoId,
            @RequestParam Long categoriaId,
            @RequestParam(required = false) Long subcategoriaId,
            @RequestParam(required = false) String nombreDocumento,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fechaDocumento,
            @RequestParam(required = false) String observacion,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) Long referenciaId) {

        LegajoDocumentoDto dto = new LegajoDocumentoDto();
        dto.setEmpleadoId(empleadoId);
        dto.setCategoriaId(categoriaId);
        dto.setSubcategoriaId(subcategoriaId);
        dto.setNombreDocumento(nombreDocumento);
        dto.setFechaDocumento(fechaDocumento);
        dto.setObservacion(observacion);
        dto.setOrigen(origen);
        dto.setReferenciaId(referenciaId);

        return new ApiResponse<>(
                "OK", "Documento subido", service.subir(dto, file));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Documento desactivado", null);
    }
}
