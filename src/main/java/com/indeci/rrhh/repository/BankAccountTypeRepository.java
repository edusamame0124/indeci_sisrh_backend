package com.indeci.rrhh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.BankAccountType;

@Repository
public interface BankAccountTypeRepository extends JpaRepository<BankAccountType, Long> {
}