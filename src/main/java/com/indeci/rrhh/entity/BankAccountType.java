package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "BANK_ACCOUNT_TYPES", schema = "GESTIONRRHH")
@Data
public class BankAccountType {

    @Id
    private Long id;

    @Column(name = "NAME")
    private String name;
}