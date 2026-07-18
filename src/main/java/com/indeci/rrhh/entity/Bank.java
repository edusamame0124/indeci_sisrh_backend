package com.indeci.rrhh.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Catálogo de bancos.
 *
 * <p><b>Tabla legacy.</b> A diferencia de las {@code INDECI_*}, {@code BANKS} no usa
 * IDENTITY: su PK se alimenta de {@code SEQ_BANKS}. Además tiene columnas obligatorias
 * que este mapeo debe cubrir o cualquier alta falla con ORA-01400: {@code CODE},
 * {@code STATUS} y {@code CREATED_AT}.
 *
 * <p>El mapeo anterior declaraba {@code IDENTITY} y omitía esas tres columnas, así que un
 * alta por JPA nunca habría funcionado. Se detectó al sembrar bancos en V012_38.
 */
@Entity
@Table(name = "BANKS", schema = "GESTIONRRHH")
@Data
public class Bank {

    /** Estado vigente en el catálogo (la columna es NOT NULL). */
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BANKS")
    @SequenceGenerator(name = "SEQ_BANKS", sequenceName = "SEQ_BANKS", allocationSize = 1)
    private Long id;

    /** Código corto del banco: BCP, BBVA, BN, INTERBANK, SCOTIA... */
    @Column(name = "CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "SHORT_NAME", length = 50)
    private String shortName;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** 1 = vigente, 0 = baja lógica (Spec 006). */
    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;
}
