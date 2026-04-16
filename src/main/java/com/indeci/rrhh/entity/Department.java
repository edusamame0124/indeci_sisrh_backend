package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;


@Entity
@Table(name = "DEPARTMENTS", schema = "GESTIONRRHH")
@Data
public class Department {

    @Id
    private String id;

    @Column(name = "NAME")
    private String name;
}