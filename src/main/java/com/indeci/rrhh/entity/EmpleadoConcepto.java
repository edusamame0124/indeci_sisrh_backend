package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_EMPLEADO_CONCEPTO",
        schema = "GESTIONRRHH"
)
@Data
public class EmpleadoConcepto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "CONCEPTO_PLANILLA_ID")
    private Long conceptoPlanillaId;

    @Column(name = "MONTO")
    private Double monto;

    @Column(name = "PORCENTAJE")
    private Double porcentaje;

    @Column(name = "FORMULA")
    private String formula;

    /** Spec 013/C1 — mes/año desde el que aplica el concepto. */
    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    /** Spec 013/C1 — mes/año hasta el que aplica. NULL = indefinido. */
    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    // --- Campos de Descuento Judicial ---
    
    @Column(name = "JUDICIAL_TIPO_CALCULO")
    private String tipoCalculoJudicial; // 'MONTO_FIJO' o 'PORCENTAJE'

    @Column(name = "JUDICIAL_BASE_CALCULO")
    private String baseCalculoJudicial;

    @Column(name = "JUDICIAL_NRO_EXPEDIENTE")
    private String nroExpediente;

    @Column(name = "JUDICIAL_NRO_OFICIO")
    private String nroOficio;

    @Column(name = "JUDICIAL_JUZGADO")
    private String juzgadoEmisor;

    @Column(name = "JUDICIAL_BENEF_TIPO_DOC")
    private String tipoDocBeneficiario;

    @Column(name = "JUDICIAL_BENEF_NRO_DOC")
    private String nroDocBeneficiario;

    @Column(name = "JUDICIAL_BENEF_NOMBRE")
    private String nombreBeneficiario;

    @Column(name = "JUDICIAL_ENTIDAD_BANCARIA")
    private String entidadBancaria;

    @Column(name = "JUDICIAL_CUENTA_BANCARIA")
    private String cuentaBancaria;
}