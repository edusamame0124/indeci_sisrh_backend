package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO_SALUD_EPS", schema = "GESTIONRRHH")
@Data
public class EmpleadoSaludEps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "TIPO_COBERTURA", nullable = false, length = 20)
    private String tipoCobertura;   // ESSALUD | ESSALUD_EPS

    @Column(name = "EPS_ID")
    private Long epsId;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;           // ACTIVO | CERRADO | INACTIVO | ANULADO

    @Column(name = "DOCUMENTO_SUSTENTO", length = 500)
    private String documentoSustento;

    @Column(name = "OBSERVACION", length = 1000)
    private String observacion;

    @Column(name = "MOTIVO_ANULACION", length = 500)
    private String motivoAnulacion;

    @Column(name = "ANULADO_POR", length = 100)
    private String anuladoPor;

    @Column(name = "ANULADO_EN")
    private LocalDateTime anuladoEn;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;
}
