package com.indeci.rrhh.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EmpleadoPlanillaDto {

    private Long empleadoId;

    // =====================================
    // CONFIGURACION BASE
    // =====================================

    /** Remuneración mensual total (calculada: monto contrato + incrementos DS). */
    private Double sueldoBasico;

    /** @deprecated UI ya no envía; backend fuerza null al persistir. */
    @Deprecated
    private Double movilidad;

    /** @deprecated UI ya no envía; backend fuerza null al persistir. */
    @Deprecated
    private Double alimentacion;

    @NotBlank(message = "Código AIRHSP obligatorio")
    @Pattern(regexp = "^[0-9]{6}$", message = "Código AIRHSP debe tener 6 dígitos numéricos")
    private String codigoAirhsp;

    @NotNull(message = "Monto contratado obligatorio")
    @DecimalMin(value = "0.01", message = "Monto contratado debe ser mayor a cero")
    private Double montoContrato;

    // =====================================
    // ASIGNACION FAMILIAR
    // =====================================

    private Integer tieneAsignacionFamiliar;

    private Integer numHijos;

    // =====================================
    // DESCUENTOS FIJOS
    // =====================================

    private Double descuentoBanco;

    private Double descuentoInstitucion;

    // =====================================
    // CONFIGURACIÓN LABORAL (mejora 2026-06-03)
    // Régimen es clave para el motor (decide 5ta/4ta, asig. familiar, topes).
    // =====================================

    private Long regimenLaboralId;

    private Long tipoContratoId;

    private Long condicionLaboralId;

    private Long modalidadCasId;

    private Long tipoPersonaMefId;

    private String registroPlazaAirhsp;

    // =====================================
    // FECHAS
    // =====================================

    private java.time.LocalDate fechaInicioContrato;
}