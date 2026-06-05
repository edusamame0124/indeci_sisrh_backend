package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Department;

import org.springframework.data.jpa.repository.JpaRepository;

/** Catálogo de departamentos (ubigeo) — usado por la Planilla CAS Consolidada. */
public interface DepartmentRepository extends JpaRepository<Department, String> {
}
