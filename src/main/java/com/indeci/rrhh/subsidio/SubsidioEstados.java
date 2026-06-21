package com.indeci.rrhh.subsidio;

/** Constantes de estado del módulo Subsidios (P0-F2). */
public final class SubsidioEstados {

    private SubsidioEstados() {}

    public static final String CASO_BORRADOR = "BORRADOR";
    public static final String CASO_PENDIENTE_VALIDACION = "PENDIENTE_VALIDACION";
    public static final String CASO_CALCULADO = "CALCULADO";
    public static final String CASO_APLICADO_PLANILLA = "APLICADO_PLANILLA";
    public static final String CASO_EN_TRAMITE_ESSALUD = "EN_TRAMITE_ESSALUD";
    public static final String CASO_CERRADO = "CERRADO";

    public static final String LIQ_BORRADOR = "BORRADOR";
    public static final String LIQ_CALCULADO = "CALCULADO";
    public static final String LIQ_APLICADO_PLANILLA = "APLICADO_PLANILLA";
    public static final String LIQ_OBSERVADO = "OBSERVADO";
    public static final String LIQ_ANULADO = "ANULADO";

    public static final String TRAMO_BORRADOR = "BORRADOR";
    public static final String TRAMO_CALCULADO = "CALCULADO";
    public static final String TRAMO_APLICADO = "APLICADO";

    public static final String MODO_OFICIAL = "OFICIAL";
    public static final String MODO_SIMULACION = "SIMULACION";

    public static final String TIPO_ENFERMEDAD = "ENFERMEDAD";
    public static final String TIPO_MATERNIDAD = "MATERNIDAD";

    public static final String IMPUT_SUBSIDIO_100 = "SUBSIDIO_100";
    public static final String IMPUT_DIFERENCIAL_2073 = "DIFERENCIAL_2073";

    public static final String SEVERIDAD_BLOQUEO = "BLOQUEO";
    public static final String SEVERIDAD_ALERTA = "ALERTA";
    public static final String SEVERIDAD_INFORMATIVA = "INFORMATIVA";
}
