package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO_BANCO", schema = "GESTIONRRHH")
@Data
public class EmpleadoBanco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "BANK_ID")
    private Long bankId;

    @Column(name = "ACCOUNT_TYPE_ID")
    private Long accountTypeId;

    @Column(name = "ACCOUNT_NUMBER")
    private String numeroCuenta;

    @Column(name = "CCI")
    private String cci;

    @Column(name = "ES_CUENTA_PLANILLA")
    private Integer esCuentaPlanilla;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "NUMERO_CUENTA_TESORERIA")
    private String numeroCuentaTesoreria;
}