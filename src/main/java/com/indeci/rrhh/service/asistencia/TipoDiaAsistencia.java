package com.indeci.rrhh.service.asistencia;

/**
 * Código interno {@code TIPO_DIA} (INDECI_ASISTENCIA_DETALLE, CHECK
 * INDECI_ASIST_DET_TIPO_CK) → etiqueta oficial para el empleado.
 *
 * <p>Espejo exacto de {@code CONDICION_LABELS} en el frontend
 * ({@code shared/utils/asistencia-display.utils.ts}) — misma redacción en pantalla y en el
 * PDF oficial de asistencia. El código interno (ej. {@code LABORAL}) nunca debe mostrarse tal
 * cual al empleado: solo alimenta el motor de cálculo y el CHECK constraint de BD.
 */
public enum TipoDiaAsistencia {
    LABORAL("Presente"),
    TARDANZA("Tardío"),
    FALTA("Falto"),
    LICENCIA("Licencia"),
    VACACIONES("Vacaciones"),
    DESCANSO("Descanso"),
    FERIADO("Feriado"),
    OBSERVADO("Observado"),
    SANCION_PAD("Sanción PAD"),
    TELETRABAJO("Teletrabajo"),
    PERMISO("Permiso c/goce"),
    OMISION_MARCACION("Omisión de marca"),
    ASISTENCIA_JUSTIFICADA("Justificada");

    private final String etiqueta;

    TipoDiaAsistencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Etiqueta oficial para el código dado. Si no corresponde a un TIPO_DIA reconocido (dato
     * nulo/corrupto), devuelve el código tal cual en vez de fallar — igual que el fallback
     * {@code CONDICION_LABELS[tipo] ?? tipo} del frontend.
     */
    public static String etiqueta(String codigo) {
        if (codigo == null) {
            return null;
        }
        try {
            return valueOf(codigo).getEtiqueta();
        } catch (IllegalArgumentException ex) {
            return codigo;
        }
    }
}
