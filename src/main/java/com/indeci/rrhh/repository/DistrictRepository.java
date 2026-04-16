package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.dto.UbigeoDto;
import com.indeci.rrhh.entity.District;

@Repository
public interface DistrictRepository extends JpaRepository<District, String> {

    @Query("""
    SELECT new com.indeci.rrhh.dto.UbigeoDto(
        d.id, d.name, p.name, dep.name
    )
    FROM District d
    JOIN Province p ON d.provinceId = p.id
    JOIN Department dep ON p.departmentId = dep.id
    """)
    List<UbigeoDto> listarUbigeoCompleto();
}