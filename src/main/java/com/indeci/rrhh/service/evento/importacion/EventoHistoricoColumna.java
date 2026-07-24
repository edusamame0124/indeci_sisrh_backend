package com.indeci.rrhh.service.evento.importacion;

/**
 * V012_42 F2 — Layout de columnas de la hoja {@code sistema} del Excel
 * "DEDUCCIONES DEL TIEMPO DE SERVICIOS" entregado por RR.HH. Fuente única de verdad
 * del orden de columnas (0-based, tal como las indexa Apache POI).
 *
 * <p>Verificado contra el archivo real (docs/DEDUCCIONES DEL TIEMPO DE SERVICIOS.xlsx,
 * hoja "sistema", A1:J455): REGIMEN, DNI, MOTIVO, SERVIDOR/A, DEPENDENCIA, SIGLAS,
 * N° DE RESOLUCION, FECHA DE INICIO, FECHA DE TERMINO, TOTAL DE DÍAS.</p>
 */
public enum EventoHistoricoColumna {
    REGIMEN(0),
    DNI(1),
    MOTIVO(2),
    SERVIDOR(3),
    DEPENDENCIA(4),
    SIGLAS(5),
    N_RESOLUCION(6),
    FECHA_INICIO(7),
    FECHA_FIN(8),
    TOTAL_DIAS(9);

    /** Nombre exacto de la hoja en el Excel oficial. */
    public static final String HOJA = "sistema";

    /** Fila 0-based donde empiezan los datos (fila 1 = encabezados en Excel 1-based). */
    public static final int PRIMERA_FILA_DATOS = 1;

    private final int indice;

    EventoHistoricoColumna(int indice) {
        this.indice = indice;
    }

    public int getIndice() {
        return indice;
    }
}
