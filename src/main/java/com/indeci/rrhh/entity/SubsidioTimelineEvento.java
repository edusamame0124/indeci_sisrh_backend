package com.indeci.rrhh.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_EVENTO")
@Data
public class SubsidioTimelineEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CASO_ID", nullable = false)
    private Long casoId;

    @Column(name = "TIPO_EVENTO", nullable = false, length = 50)
    private String tipoEvento;

    @Column(name = "DESCRIPCION", length = 500)
    private String descripcion;

    @Lob
    @Column(name = "DETALLE_JSON")
    private String detalleJson;

    @Column(name = "USUARIO", length = 60)
    private String usuario;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
