package com.indeci.rrhh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.Bank;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {
}