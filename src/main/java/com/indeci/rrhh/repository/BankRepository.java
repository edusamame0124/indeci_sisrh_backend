package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.Bank;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {

    List<Bank> findByActivoOrderByNameAsc(Integer activo);

    boolean existsByNameIgnoreCaseAndActivo(String name, Integer activo);

    boolean existsByNameIgnoreCaseAndActivoAndIdNot(String name, Integer activo, Long id);

    Optional<Bank> findByIdAndActivo(Long id, Integer activo);
}