package com.indeci.rrhh.controller;

import java.io.IOException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.common.dto.ApiResponse;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.vinculacion.importacion.VinculacionImportDtos.CommitDto;
import com.indeci.rrhh.vinculacion.importacion.VinculacionImportDtos.PreviewDto;
import com.indeci.rrhh.vinculacion.importacion.VinculacionImportService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * Import masivo de vinculación laboral (Registro Integrado de Personal).
 *
 * <p>Flujo de dos fases: {@code /preview} valida sin escribir y {@code /commit} persiste solo
 * las filas correctas. La previsualización es obligatoria por diseño — el import escribe en
 * 7 tablas y alimenta al motor de planilla.
 */
@RestController
@RequestMapping("/api/rrhh/vinculacion/import")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
public class VinculacionImportController {

    /** Tope defensivo: la plantilla real (617 filas) pesa ~0.6 MB. */
    private static final long TAMANO_MAXIMO_BYTES = 15L * 1024 * 1024;

    private final VinculacionImportService importService;

    /** Valida el archivo y devuelve el estado fila por fila. No escribe nada. */
    @PostMapping("/preview")
    public ApiResponse<PreviewDto> preview(@RequestParam("archivo") MultipartFile archivo) {
        return new ApiResponse<>("OK",
                "Vista previa generada. No se guardó ningún dato.",
                importService.previsualizar(leer(archivo)));
    }

    /** Importa las filas sin errores. Idempotente por DNI + N.° de contrato. */
    @PostMapping("/commit")
    public ApiResponse<CommitDto> commit(@RequestParam("archivo") MultipartFile archivo) {
        final CommitDto resultado = importService.importar(leer(archivo));
        return new ApiResponse<>("OK",
                String.format("Importación completada: %d creados, %d actualizados, %d omitidos.",
                        resultado.creados(), resultado.actualizados(), resultado.omitidos()),
                resultado);
    }

    private byte[] leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("Debe adjuntar el archivo Excel de vinculación.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new NegocioException("El archivo supera el tamaño máximo permitido (15 MB).");
        }
        final String nombre = archivo.getOriginalFilename();
        if (nombre != null && !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new NegocioException("El archivo debe ser .xlsx (plantilla oficial de vinculación).");
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el archivo: " + e.getMessage());
        }
    }
}
