package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO_PENSION", schema = "GESTIONRRHH")
@Data
public class EmpleadoPension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "REGIMEN_PENSIONARIO_ID")
    private Long regimenPensionarioId;

    @Column(name = "TIPO_REGIMEN")
    private String tipoRegimen;

    @Column(name = "CUSPP")
    private String cuspp;

    @Column(name = "PORCENTAJE_APORTE")
    private Double porcentajeAporte;

    @Column(name = "PORCENTAJE_COMISION")
    private Double porcentajeComision;

    @Column(name = "PORCENTAJE_SEGURO")
    private Double porcentajeSeguro;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "TIPO_COMISION_AFP_ID")
    private Long tipoComisionAfpId;

    @Column(name = "CONDICION_ESPECIAL_AFP")
    private String condicionEspecialAfp;

    @Column(name = "FECHA_CONDICION_AFP")
    private LocalDate fechaCondicionAfp;

    @Column(name = "DOCUMENTO_SUSTENTO_ID")
    private Long documentoSustentoId;

    @Column(name = "OBSERVACION_CONDICION_AFP")
    private String observacionCondicionAfp;
}
