package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Province;

import org.springframework.data.jpa.repository.JpaRepository;

/** Catálogo de provincias (ubigeo) — usado por la Planilla CAS Consolidada. */
public interface ProvinceRepository extends JpaRepository<Province, String> {
}
