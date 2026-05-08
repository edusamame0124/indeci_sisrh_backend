package com.indeci.admin.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.admin.support.AdminAuditDateParser;
import com.indeci.audit.entity.Auditoria;
import com.indeci.audit.repository.AuditoriaRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    @Transactional(readOnly = true)
    public Page<Auditoria> query(
            String usuario,
            String accion,
            String fechaDesde,
            String fechaHasta,
            String ip,
            Pageable pageable) {

        var desde = AdminAuditDateParser.parseInicio(fechaDesde);
        var hasta = AdminAuditDateParser.parseFin(fechaHasta);
        Specification<Auditoria> spec = buildSpec(usuario, accion, desde, hasta, ip);
        return auditoriaRepository.findAll(spec, pageable);
    }

    private static Specification<Auditoria> buildSpec(
            String usuario,
            String accion,
            java.time.LocalDateTime desde,
            java.time.LocalDateTime hasta,
            String ip) {

        return (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            if (usuario != null && !usuario.isBlank()) {
                String e = escapeLike(usuario.trim());
                parts.add(cb.like(cb.lower(root.get("usuario")), "%" + e.toLowerCase(Locale.ROOT) + "%", '\\'));
            }
            if (accion != null && !accion.isBlank()) {
                String e = escapeLike(accion.trim());
                parts.add(cb.like(cb.lower(root.get("accion")), "%" + e.toLowerCase(Locale.ROOT) + "%", '\\'));
            }
            if (ip != null && !ip.isBlank()) {
                String e = escapeLike(ip.trim());
                parts.add(cb.like(root.get("ip"), "%" + e + "%", '\\'));
            }
            if (desde != null) {
                parts.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            }
            if (hasta != null) {
                parts.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta));
            }
            if (parts.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(parts.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
