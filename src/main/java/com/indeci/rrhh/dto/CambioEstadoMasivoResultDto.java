package com.indeci.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Alineado con CambioEstadoMasivoResult del frontend (exitosos/omitidos/errores). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CambioEstadoMasivoResultDto {
    private int exitosos;
    private int omitidos;
    private List<String> errores;
}
