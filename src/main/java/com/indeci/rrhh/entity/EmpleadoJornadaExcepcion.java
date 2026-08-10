package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Horario especial (excepción) de un trabajador puntual, con vigencia propia.
 * Override sobre {@link JornadaRegimen}: mientras esté vigente, sus horas de
 * ingreso/salida/refrigerio mandan sobre las del régimen del empleado — el
 * resto (tolerancias, umbral/tope de tardanza, jornada en horas) se hereda
 * siempre del régimen (ver {@code EmpleadoJornadaResolver}).
 */
@Entity
@Table(name = "INDECI_EMPLEADO_JORNADA_EXCEPCION", schema = "GESTIONRRHH")
@Data
public class EmpleadoJornadaExcepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "HORA_INGRESO", length = 5, nullable = false)
    private String horaIngreso;

    @Column(name = "HORA_SALIDA", length = 5, nullable = false)
    private String horaSalida;

    @Column(name = "REFRIGERIO_INICIO", length = 5)
    private String refrigerioInicio;

    @Column(name = "REFRIGERIO_FIN", length = 5)
    private String refrigerioFin;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "DOCUMENTO_AUTORIZACION", length = 200, nullable = false)
    private String documentoAutorizacion;

    @Column(name = "MOTIVO", length = 500)
    private String motivo;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** ¿Esta excepción cubre la fecha dada? (espejo de PapeletaJustificacionResolver.cubreFecha). */
    public boolean cubre(LocalDate fecha) {
        return fecha != null && !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }
}
