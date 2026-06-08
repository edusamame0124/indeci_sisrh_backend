package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_TIPO_DESCANSO_DOC",
        schema = "GESTIONRRHH")
@Data
public class TipoDescansoDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TIPO_DESCANSO_ID")
    private Long tipoDescansoId;

    @Column(name = "DOCUMENTO_ID")
    private Long documentoId;

    @Column(name = "ACTIVO")
    private Integer activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "DOCUMENTO_ID",
            insertable = false,
            updatable = false)
    private DocumentoRequerido documento;
}