package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ModalidadCas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModalidadCasRepository extends JpaRepository<ModalidadCas, Long> {

    @Query("SELECT m FROM ModalidadCas m WHERE m.activo = 1 ORDER BY m.nombre ASC")
    List<ModalidadCas> findAllActivos();
}
