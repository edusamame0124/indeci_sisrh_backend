package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * SPEC_HOMOLOGACION_MGRH §E — DTO de lectura del catálogo MGRH/MEF.
 *
 * <p>Expone todos los campos oficiales del concepto MGRH + la metadata de versión
 * anual ({@code anioCatalogo}/{@code vigente}/{@code fuenteCatalogo}) y la bandera
 * {@code seleccionable}, para que la pestaña "Homologación MGRH / MEF" muestre el
 * detalle solo-lectura sin segunda llamada.</p>
 */
@Data
public class CatalogoConceptoMgrhDto {

    private Long id;
    private String tipo;
    private String codigoConceptoMgrh;
    private String descripcionNorma;
    private String detalleNorma;
    private String fechaVigenciaTexto;
    private LocalDate fechaVigenciaDate;
    private String imponible;
    private String descripcionTipoConcepto;
    private String tipoNorma;
    private String estado;
    private String seleccionable;
    private Integer anioCatalogo;
    private String vigente;
    private String fuenteCatalogo;
}
