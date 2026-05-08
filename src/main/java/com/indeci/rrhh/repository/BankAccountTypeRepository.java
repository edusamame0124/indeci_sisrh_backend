package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.BankAccountType;

@Repository
public interface BankAccountTypeRepository extends JpaRepository<BankAccountType, Long> {

    List<BankAccountType> findByActivoOrderByNameAsc(Integer activo);

    boolean existsByNameIgnoreCaseAndActivo(String name, Integer activo);

    boolean existsByNameIgnoreCaseAndActivoAndIdNot(String name, Integer activo, Long id);

    Optional<BankAccountType> findByIdAndActivo(Long id, Integer activo);
}