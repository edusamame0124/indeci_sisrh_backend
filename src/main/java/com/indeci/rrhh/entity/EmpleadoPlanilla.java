package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO_PLANILLA", schema = "GESTIONRRHH")
@Data
public class EmpleadoPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "SUELDO_BASICO")
    private Double sueldoBasico;

    @Column(name = "ASIGNACION_TRANSPORTE")
    private Double movilidad;

    @Column(name = "ASIGNACION_ALIMENTACION")
    private Double alimentacion;

    @Column(name = "TIENE_ASIGNACION_FAMILIAR")
    private Integer tieneAsignacionFamiliar;

    @Column(name = "NUM_HIJOS")
    private Integer numHijos;

    @Column(name = "DESCUENTO_BANCO")
    private Double descuentoBanco;

    @Column(name = "DESCUENTO_JUDICIAL")
    private Double descuentoInstitucion;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_INICIO_CONTRATO")
    private LocalDate fechaInicioContrato;

    /** SPEC_VACACIONES F9.1 — override de jornada por empleado (NULL=hereda del régimen). 6=operativo COEN/DDI. */
    @Column(name = "DIAS_SEMANA_OPERATIVO")
    private Integer diasSemanaOperativo;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "REGIMEN_LABORAL_ID")
    private Long regimenLaboralId;

    @Column(name = "TIPO_CONTRATO_ID")
    private Long tipoContratoId;

    @Column(name = "CONDICION_LABORAL_ID")
    private Long condicionLaboralId;

    @Column(name = "MODALIDAD_CAS_ID")
    private Long modalidadCasId;

    /** V012_07 — Ley 30057: Funcionario/Directivo/Carrera/Actividades complementarias. */
    @Column(name = "GRUPO_SERVIDOR_CIVIL")
    private String grupoServidorCivil;

    /** V012_07 — condición de confianza (1=Sí, 0=No). No es un grupo aparte. */
    @Column(name = "ES_CONFIANZA")
    private Integer esConfianza;

    @Column(name = "TIPO_PERSONA_MEF_ID")
    private Long tipoPersonaMefId;

    @Column(name = "REGISTRO_PLAZA_AIRHSP")
    private String registroPlazaAirhsp;

    @Column(name = "NUMERO_CONTRATO")
    private String numeroContrato;

    /**
     * V012_37 — N.° del proceso de selección (CAS). Texto libre: RR.HH. registra desde
     * '073-2012' hasta 'R J N° 000193-2024-INDECI/JEF' o 'REPOSICION JUDICIAL'.
     */
    @Column(name = "NRO_CONVOCATORIA")
    private String nroConvocatoria;

    /**
     * V012_37 — base legal que sustenta el vínculo ('DECRETO URGENCIA Nº 034-2021',
     * 'LEY Nº 31131 - INDETERMINADO'). NO confundir con CONDICION_LABORAL_ID, que es el
     * catálogo NOMBRADO/CONTRATADO y solo aplica al régimen 276.
     */
    @Column(name = "BASE_LEGAL_VINCULO")
    private String baseLegalVinculo;

    @Column(name = "FECHA_INGRESO")
    private LocalDate fechaIngreso;

    @Column(name = "FECHA_CESE")
    private LocalDate fechaCese;

    /** V012_04 — hecho registrado por RR.HH.; alimenta la derivación del estado. */
    @Column(name = "MOTIVO_CESE")
    private String motivoCese;

    /** V012_04 — documento que sustenta el cese (resolución/carta/memorando). */
    @Column(name = "DOCUMENTO_CESE")
    private String documentoCese;

    /** V012_08 — sustento de origen del vínculo (contrato/resolución/adenda/designación). */
    @Column(name = "DOCUMENTO_ORIGEN_TIPO")
    private String documentoOrigenTipo;

    @Column(name = "DOCUMENTO_ORIGEN_NUMERO")
    private String documentoOrigenNumero;

    @Column(name = "DOCUMENTO_ORIGEN_FECHA")
    private LocalDate documentoOrigenFecha;

    /** Legacy. El estado canónico del vínculo se DERIVA (ver VinculoEstadoResolver). */
    @Column(name = "ESTADO_LABORAL")
    private String estadoLaboral;

    @Column(name = "CODIGO_AIRHSP")
    private String codigoAirhsp;

    /** Sueldo pactado en contrato (base antes de incrementos DS). */
    @Column(name = "MONTO_CONTRATO")
    private Double montoContrato;

    @Column(name = "META")
    private String meta;

    @Column(name = "FUENTE_FINANCIAMIENTO")
    private String fuenteFinanciamiento;

    @Column(name = "CENTRO_COSTO")
    private String centroCosto;

    @Column(name = "OBSERVACION")
    private String observacion;

    /** Aviso normativo del vínculo (Excel import "PLAZO MAXIMO"); se muestra en Config. Remunerativa. */
    @Column(name = "PLAZO_MAXIMO")
    private String plazoMaximo;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
    
    
    @Column(name = "TIENE_AIRHSP")
    private Integer tieneAirhsp;

    /**
     * Gate de Modalidad de Teletrabajo (Ley N° 31572, V012_28): 1 = el servidor
     * cuenta con resolución/adenda de teletrabajo activa en su legajo y puede
     * generar reportes diarios de teletrabajo. Default 0.
     */
    @Column(name = "ES_TELETRABAJADOR")
    private Integer esTeletrabajador;

    @Column(name = "AIRHSP_VIGENCIA_ID")
    private Long airhspVigenciaId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "REGIMEN_LABORAL_ID",
            insertable = false,
            updatable = false)
    private RegimenLaboral regimenLaboral;
}