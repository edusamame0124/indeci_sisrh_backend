package com.indeci.rrhh.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_VALIDACION")
@Data
public class SubsidioValidacionRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CASO_ID", nullable = false)
    private Long casoId;

    @Column(name = "TRAMO_ID")
    private Long tramoId;

    @Column(name = "LIQUIDACION_ID")
    private Long liquidacionId;

    @Column(name = "CODIGO_VALIDACION", nullable = false, length = 20)
    private String codigoValidacion;

    @Column(name = "SEVERIDAD", nullable = false, length = 20)
    private String severidad;

    @Column(name = "MENSAJE_USUARIO", nullable = false, length = 500)
    private String mensajeUsuario;

    @Column(name = "DETALLE_TECNICO", length = 1000)
    private String detalleTecnico;

    @Column(name = "RESUELTA", nullable = false, length = 1)
    private String resuelta;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
