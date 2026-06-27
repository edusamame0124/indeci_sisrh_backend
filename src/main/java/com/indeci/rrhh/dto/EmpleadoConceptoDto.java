package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmpleadoConceptoDto {

    private Long empleadoId;

    private Long conceptoPlanillaId;

    private Double monto;

    private Double porcentaje;

    private String formula;

    /** Spec 013/C1 — vigencia: mes/año de inicio (obligatorio en el modal). */
    private LocalDate fechaInicio;

    /** Spec 013/C1 — vigencia: mes/año de fin. NULL = indefinido. */
    private LocalDate fechaFin;

    // --- Campos de Descuento Judicial ---
    private String tipoCalculoJudicial; // 'MONTO_FIJO' o 'PORCENTAJE'
    private String baseCalculoJudicial;
    private String nroExpediente;
    private String nroOficio;
    private String juzgadoEmisor;
    private String tipoDocBeneficiario;
    private String nroDocBeneficiario;
    private String nombreBeneficiario;
    private String entidadBancaria;
    private String cuentaBancaria;
}