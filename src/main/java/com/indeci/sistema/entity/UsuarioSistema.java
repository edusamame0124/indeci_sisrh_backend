package com.indeci.sistema.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Fase 3 SSO — Asignación usuario × sistema con roles externos opacos al SISRH.
 * Tabla INDECI_USUARIO_SISTEMA (V010_35).
 *
 * ROLES_EXTERNOS es un VARCHAR2 con un array JSON (p. ej. ["EVALUADOR","CONSULTA"]).
 * El parseo a List&lt;String&gt; lo hace {@code UsuarioSistemaService} con Jackson —
 * la entity solo expone el String crudo para mantenerse libre de dependencias.
 *
 * NO almacena roles del SISRH propio (esos siguen en INDECI_USUARIO_ROL).
 */
@Entity
@Table(name = "INDECI_USUARIO_SISTEMA", schema = "GESTIONRRHH")
@Data
public class UsuarioSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "SISTEMA_ID", nullable = false)
    private Long sistemaId;

    /** Array JSON crudo. NULL = card bloqueada en el selector. */
    @Column(name = "ROLES_EXTERNOS", length = 500)
    private String rolesExternos;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
