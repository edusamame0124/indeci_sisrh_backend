package com.indeci.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.indeci.audit.entity.Auditoria;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
}