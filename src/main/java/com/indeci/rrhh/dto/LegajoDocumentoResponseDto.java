package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * F2.6 — Response al consultar / crear un documento del legajo. Denormaliza
 * el nombre de categoría/subcategoría para que la UI no haga 2 GET extra.
 */
@Data
public class LegajoDocumentoResponseDto {

    private Long id;
    private Long empleadoId;

    private Long categoriaId;
    private String categoriaNombre;

    private Long subcategoriaId;
    private String subcategoriaNombre;

    private String nombreDocumento;
    private String nombreArchivo;
    /** Ruta FTP (basePath + carpeta + archivo). Sólo para auditoría/descarga interna. */
    private String rutaArchivo;
    private String extension;
    private Long pesoArchivo;

    private LocalDate fechaDocumento;
    private String observacion;

    private String origen;
    private Long referenciaId;
    private Integer versionDoc;

    private LocalDateTime createdAt;
    private String createdBy;
}
