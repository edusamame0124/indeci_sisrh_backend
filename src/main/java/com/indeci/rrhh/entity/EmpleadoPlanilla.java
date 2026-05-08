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

    @Column(name = "NUMERO_CONTRATO")
    private String numeroContrato;

    @Column(name = "FECHA_INGRESO")
    private LocalDate fechaIngreso;

    @Column(name = "FECHA_CESE")
    private LocalDate fechaCese;

    @Column(name = "ESTADO_LABORAL")
    private String estadoLaboral;

    @Column(name = "CODIGO_AIRHSP")
    private String codigoAirhsp;

    @Column(name = "META")
    private String meta;

    @Column(name = "FUENTE_FINANCIAMIENTO")
    private String fuenteFinanciamiento;

    @Column(name = "CENTRO_COSTO")
    private String centroCosto;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
    
    
    @Column(name = "TIENE_AIRHSP")
    private Integer tieneAirhsp;

    @Column(name = "AIRHSP_VIGENCIA_ID")
    private Long airhspVigenciaId;
}