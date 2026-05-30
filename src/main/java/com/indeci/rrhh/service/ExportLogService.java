package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ExportArchivoResponseDto;
import com.indeci.rrhh.entity.ExportArchivo;
import com.indeci.rrhh.repository.ExportArchivoRepository;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M09 — Registro de exportaciones PLAME/MCPP (log INDECI_EXPORT_ARCHIVO).
 *
 * <p>Persiste cada archivo generado con su hash SHA-256 (integridad / no-repudio),
 * conteo de líneas y totales. La traza quién/cuándo la lleva AuditoriaAspect.
 */
@Service
@RequiredArgsConstructor
public class ExportLogService {

    private final ExportArchivoRepository repository;

    /** Registra un archivo generado. El contenido se hashea como ISO-8859-1 (bytes de descarga). */
    @Transactional
    public ExportArchivo registrar(String periodo, String tipoArchivo, String nombreArchivo,
                                   String contenido, BigDecimal totalIngresos,
                                   BigDecimal totalDescuentos) {
        ExportArchivo e = new ExportArchivo();
        e.setPeriodo(periodo);
        e.setTipoArchivo(tipoArchivo);
        e.setNombreArchivo(nombreArchivo);
        e.setHashSha256(sha256(contenido));
        e.setNroLineas(contarLineas(contenido));
        e.setTotalIngresos(totalIngresos);
        e.setTotalDescuentos(totalDescuentos);
        e.setFechaGenerado(LocalDateTime.now());
        return repository.save(e);
    }

    /** Historial de exportaciones del período, más reciente primero. */
    @Transactional(readOnly = true)
    public List<ExportArchivoResponseDto> historial(String periodo) {
        return repository.findByPeriodoOrderByFechaGeneradoDesc(periodo)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================ HELPERS ============================

    static String sha256(String contenido) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(contenido.getBytes(StandardCharsets.ISO_8859_1));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new NegocioException("No se pudo calcular el hash SHA-256 del archivo");
        }
    }

    /** Número de líneas terminadas en salto (CRLF). Cadena vacía → 0. */
    static int contarLineas(String contenido) {
        if (contenido == null || contenido.isEmpty()) {
            return 0;
        }
        int lineas = 0;
        for (int i = 0; i < contenido.length(); i++) {
            if (contenido.charAt(i) == '\n') {
                lineas++;
            }
        }
        return lineas;
    }

    private ExportArchivoResponseDto toResponse(ExportArchivo e) {
        ExportArchivoResponseDto dto = new ExportArchivoResponseDto();
        dto.setId(e.getId());
        dto.setPeriodo(e.getPeriodo());
        dto.setTipoArchivo(e.getTipoArchivo());
        dto.setNombreArchivo(e.getNombreArchivo());
        dto.setHashSha256(e.getHashSha256());
        dto.setNroLineas(e.getNroLineas() == null ? 0 : e.getNroLineas());
        dto.setTotalIngresos(e.getTotalIngresos());
        dto.setTotalDescuentos(e.getTotalDescuentos());
        dto.setGeneradoPor(e.getGeneradoPor());
        dto.setFechaGenerado(e.getFechaGenerado());
        dto.setNroTicketMcpp(e.getNroTicketMcpp());
        return dto;
    }
}
