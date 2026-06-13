package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_SOLICITUD_RRHH_DOC",
        schema = "GESTIONRRHH")
@Data
public class SolicitudRrhhDoc {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SOLICITUD_ID")
    private Long solicitudId;

    @Column(name = "ETAPA")
    private String etapa;

    @Column(name = "NOMBRE_ARCHIVO")
    private String nombreArchivo;

    @Column(name = "RUTA_ARCHIVO")
    private String rutaArchivo;

    @Column(name = "VERSION_DOC")
    private Integer versionDoc;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "USUARIO_UPLOAD")
    private String usuarioUpload;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "ACTIVO")
    private Integer activo;
    
    @Column(name = "MIME_TYPE")
    private String mimeType;

    @Column(name = "TAMANIO_BYTES")
    private Long tamanioBytes;
    
    @Column(name = "DOCUMENTO_REQUERIDO_ID")
    private Long documentoRequeridoId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "DOCUMENTO_REQUERIDO_ID",
            insertable = false,
            updatable = false)
    private DocumentoRequerido documentoRequerido;
}