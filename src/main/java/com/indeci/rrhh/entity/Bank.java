package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
@Entity
@Table(name = "BANKS", schema = "GESTIONRRHH")
@Data
public class Bank {

    @Id
    private Long id;

    @Column(name = "NAME")
    private String name;
}