package com.indeci.rrhh.vinculacion.importacion;

/**
 * Contrato de columnas de la hoja {@code VINCULACION} — fuente única de verdad.
 *
 * <p>Layout del machote oficial entregado por RR.HH.
 * ({@code docs/PLANTILLA_IMPORT_VINCULACION_oficial.xlsx}):
 * <ul>
 *   <li>Fila 1 → cabecera de bloque (decorativa, no se lee)</li>
 *   <li>Fila 2 → cabecera de campo (se valida contra {@link #getCabecera()})</li>
 *   <li>Fila 3+ → datos (una fila = un vínculo de un empleado)</li>
 * </ul>
 *
 * <p>El orden es <b>fijo</b>: el lector accede por índice, no por nombre. Si RR.HH.
 * agrega o mueve una columna, se cambia <b>solo aquí</b>.
 *
 * <p>Columnas marcadas {@link #isDerivada()} se leen pero <b>no se persisten</b>: el
 * sistema las calcula (edad de la fecha de nacimiento; provincia/departamento del
 * distrito; tiempo de servicios de la fecha de ingreso). Importarlas duplicaría la
 * verdad y abriría inconsistencias.
 */
public enum VinculacionColumna {

    // ---- Bloque A — Identificación de la persona
    TIPO_DOCUMENTO(0, "Tipo de documento"),
    DNI(1, "DNI"),
    NOMBRE_COMPLETO(2, "Apellidos y nombres"),
    FECHA_NACIMIENTO(3, "Fecha de nacimiento"),
    SEXO(4, "Sexo"),
    ESTADO_CIVIL(5, "Estado civil"),
    NACIONALIDAD(6, "Nacionalidad"),
    EDAD(7, "EDAD", true),
    RUC(8, "RUC"),
    CLASE_PERSONAL(9, "PERSONA"),
    CORREO_PERSONAL(10, "Correo personal"),
    CORREO_INSTITUCIONAL(11, "Correo institucional"),
    TELEFONO(12, "Telefono / celular"),
    DIRECCION(13, "Direccion"),
    PROVINCIA(14, "PROVINCIA", true),
    DEPARTAMENTO(15, "DEPARTAMENTO", true),
    DISTRITO(16, "Distrito (Ubigeo)"),
    PROFESION(17, "Profesion"),
    GRADO_ACADEMICO(18, "Grado academico"),
    NIVEL_INSTRUCCION(19, "Nivel de instruccion"),

    // ---- Formación académica (→ INDECI_FORMACION_ACADEMICA, módulo Legajo)
    ENTIDAD_UNIVERSIDAD(20, "ENTIDAD - UNIVERSIDAD"),
    FECHA_GRADO(21, "FECHA DE GRADO"),
    NIVEL_POSGRADO(22, "NIVEL_POSGRADO"),
    CONDICION_GRADO(23, "CONDICION/GRADO"),
    ESPECIALIDAD_POSGRADO(24, "ESPECIALIDAD_POSGRADO"),

    // ---- Bloque B — Datos del empleado
    CONADIS(25, "Codigo CONADIS (discapacidad)"),
    TIENE_EPS(26, "¿Afiliado a EPS? (S/N)"),

    // ---- Bloque C — Vínculo laboral
    CODIGO_AIRHSP(27, "Codigo AIRHSP de la plaza"),
    REGIMEN_LABORAL(28, "Regimen laboral  *CLAVE*"),
    TIPO_CONTRATO(29, "Tipo de contrato"),
    CONDICION_LABORAL(30, "Condicion laboral (SOLO APLICA PARA 276)"),
    MODALIDAD_CAS(31, "Modalidad CAS (NECESIDAD TRANSITORIA, CONFIANZA, SUPLENCIA)"),
    GRUPO_SERVIDOR_CIVIL(32, "Grupo servidor civil (Ley 30057)"),
    ES_CONFIANZA(33, "¿Cargo de confianza? (S/N)"),
    CARGO(34, "Cargo"),
    NIVEL(35, "Nivel"),
    NRO_CONVOCATORIA(36, "NRO DE CONVOCATORIA"),
    NUMERO_CONTRATO(37, "N° de contrato"),
    MONTO_CONTRATO(38, "Monto contratado (S/)"),
    META(39, "Meta presupuestal"),
    FUENTE_FINANCIAMIENTO(40, "Fuente de financiamiento"),
    CENTRO_COSTO(41, "Centro de costo"),
    CATEGORIA_PRESUPUESTAL(42, "CATEGORIA PRESUPUESTAL"),
    ES_TELETRABAJADOR(43, "¿Teletrabajador? (S/N)"),
    DIAS_JORNADA(44, "Dias de jornada semanal"),

    // ---- Bloque D — Fechas y documento de origen
    FECHA_INGRESO(45, "Fecha de ingreso a la entidad"),
    FECHA_INICIO_CONTRATO(46, "Fecha inicio del contrato"),
    TIEMPO_SERVICIOS(47, "TIEMPO DE SERVICIOS", true),
    FECHA_FIN(48, "Fecha fin del contrato"),
    DOCUMENTO_ORIGEN_TIPO(49, "Tipo doc. de origen"),
    DOCUMENTO_ORIGEN_NUMERO(50, "N° doc. de origen"),
    DOCUMENTO_ORIGEN_FECHA(51, "Fecha doc. de origen"),

    // ---- Bloque E — Cese
    FECHA_CESE(52, "Fecha de cese"),
    MOTIVO_CESE(53, "Motivo de cese"),
    DOCUMENTO_CESE(54, "Documento de cese"),

    // ---- Bloque F — Régimen pensionario
    SISTEMA_PENSIONARIO(55, "Sistema pensionario"),
    AFP(56, "AFP (si aplica)"),
    CUSPP(57, "CUSPP"),
    TIPO_COMISION_AFP(58, "Tipo de comision AFP"),
    FECHA_AFILIACION(59, "Fecha de afiliacion"),

    // ---- Bloque G — Puesto / ubicación
    SEDE(60, "Sede"),
    OFICINA(61, "Oficina / dependencia"),
    JEFE_DNI(62, "DNI del jefe inmediato"),

    // ---- Bloque H — Asignación familiar
    TIENE_ASIGNACION_FAMILIAR(63, "¿Tiene asignación familiar? (S/N)"),
    NUM_HIJOS(64, "N° de hijos menores"),

    // ---- Bloque J — Datos bancarios (→ INDECI_EMPLEADO_BANCO)
    BANCO(65, "BANCO"),
    NUMERO_CUENTA(66, "N° de cuenta"),
    CCI(67, "CCI"),

    // ---- Bloque K — Cobertura de salud (→ INDECI_EMPLEADO_SALUD_EPS)
    // El tipo de cobertura se deriva de "¿Afiliado a EPS?": N→ESSALUD, S→ESSALUD_EPS.
    ESSALUD_FECHA_INICIO(68, "Fecha inicio vigencia EsSalud"),

    // ---- Aviso normativo del vínculo (texto libre; se muestra en Config. Remunerativa)
    PLAZO_MAXIMO(69, "PLAZO MAXIMO");

    /** Nombre de la hoja de datos. */
    public static final String HOJA = "VINCULACION";
    /** Fila (0-based) de la cabecera de campo. */
    public static final int FILA_CABECERA = 1;
    /** Primera fila (0-based) con datos. */
    public static final int PRIMERA_FILA_DATOS = 2;

    private final int indice;
    private final String cabecera;
    private final boolean derivada;

    VinculacionColumna(int indice, String cabecera) {
        this(indice, cabecera, false);
    }

    VinculacionColumna(int indice, String cabecera, boolean derivada) {
        this.indice = indice;
        this.cabecera = cabecera;
        this.derivada = derivada;
    }

    /** Índice 0-based de la columna en la hoja. */
    public int getIndice() {
        return indice;
    }

    /** Cabecera esperada en la fila 2 (referencia para el chequeo de layout). */
    public String getCabecera() {
        return cabecera;
    }

    /** {@code true} si el sistema la calcula y por tanto no se persiste. */
    public boolean isDerivada() {
        return derivada;
    }

    /** Letra de columna Excel (A, B, ..., BP). Solo para mensajes de error legibles. */
    public String getLetra() {
        final StringBuilder sb = new StringBuilder();
        int n = indice;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return sb.toString();
    }
}
