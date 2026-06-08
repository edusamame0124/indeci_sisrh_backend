package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_SOLICITUD_RRHH",
        schema = "GESTIONRRHH"
)
@Data
public class SolicitudRrhh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "TIPO_SOLICITUD_ID")
    private Long tipoSolicitudId;
    


    @Column(name = "ESTADO_SOLICITUD_ID")
    private Long estadoSolicitudId;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CANTIDAD_DIAS")
    private Double cantidadDias;

    @Column(name = "MOTIVO")
    private String motivo;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "APROBADO_POR")
    private Long aprobadoPor;

    @Column(name = "FECHA_APROBACION")
    private LocalDateTime fechaAprobacion;

    @Column(name = "ARCHIVO_SUSTENTO")
    private String archivoSustento;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "HORA_INICIO")
    private String horaInicio;

    @Column(name = "HORA_FIN")
    private String horaFin;

    @Column(name = "CANTIDAD_HORAS")
    private Double cantidadHoras;
    
    @Column(name = "LUGAR_COMISION")
    private String lugarComision;
    
    @Column(name = "FECHA_NACIMIENTO_HIJO")
    private LocalDate fechaNacimientoHijo;

    @Column(name = "FECHA_FIN_POSTNATAL")
    private LocalDate fechaFinPostnatal;

    @Column(name = "MINUTOS_INGRESO")
    private Integer minutosIngreso;

    @Column(name = "MINUTOS_SALIDA")
    private Integer minutosSalida;
    
    @Column(name = "TIPO_DESCANSO_MEDICO_ID")
    private Long tipoDescansoMedicoId;

    @Column(name = "NOMBRE_MEDICO")
    private String nombreMedico;

    @Column(name = "NUMERO_COLEGIATURA")
    private String numeroColegiatura;
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "TIPO_DESCANSO_MEDICO_ID",
            insertable = false,
            updatable = false)
    private TipoDescansoMedico tipoDescansoMedico;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="TIPO_SOLICITUD_ID",
    		insertable = false,
    		updatable = false)
    private TipoSolicitudRrhh tipoSolicitud;
}