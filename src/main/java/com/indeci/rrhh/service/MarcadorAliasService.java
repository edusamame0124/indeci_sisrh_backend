package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MarcadorAliasDto;
import com.indeci.rrhh.dto.MarcadorAliasRequest;
import com.indeci.rrhh.dto.MarcadorSinMapeoDto;
import com.indeci.rrhh.entity.EmpleadoMarcadorAlias;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.EmpleadoMarcadorAliasRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.service.asistencia.NombreMarcadorNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapeo de identidad del marcador COEN (SPEC D1): lista los nombres sin mapear de
 * una importación y crea/actualiza el alias nombre↔empleado. Al mapear, el próximo
 * preview auto-empareja esos días. Nunca da de alta empleados (eso es M03).
 */
@Service
@RequiredArgsConstructor
public class MarcadorAliasService {

    private static final String ORIGEN_DEFECTO = "COEN";

    private final AsistenciaImportacionFilaRepository filaRepository;
    private final EmpleadoMarcadorAliasRepository aliasRepository;
    private final EmpleadoRepository empleadoRepository;

    @Transactional(readOnly = true)
    public List<MarcadorSinMapeoDto> listarSinMapeo(Long importacionId) {
        return filaRepository.resumenSinMapeo(importacionId).stream()
                .map(r -> new MarcadorSinMapeoDto(
                        (String) r[0],
                        ((Number) r[1]).longValue()))
                .toList();
    }

    @Auditable(accion = "MAPEAR_ALIAS_MARCADOR")
    @Transactional
    public MarcadorAliasDto mapear(MarcadorAliasRequest request) {
        if (request == null || request.getEmpleadoId() == null) {
            throw new NegocioException("Debe indicar el empleado al cual mapear el nombre.");
        }
        if (request.getNombreMarcador() == null || request.getNombreMarcador().isBlank()) {
            throw new NegocioException("El nombre del marcador es obligatorio.");
        }
        if (!empleadoRepository.existsById(request.getEmpleadoId())) {
            throw new NegocioException("El empleado indicado no existe.");
        }

        String origen = (request.getOrigen() == null || request.getOrigen().isBlank())
                ? ORIGEN_DEFECTO
                : request.getOrigen().trim().toUpperCase();
        String norm = NombreMarcadorNormalizer.normalizar(request.getNombreMarcador());
        String usuario = usuarioActual();

        // Upsert: si el nombre ya estaba mapeado (a otro empleado), se re-apunta.
        EmpleadoMarcadorAlias alias = aliasRepository
                .findFirstByNombreMarcadorNormAndActivo(norm, 1)
                .orElseGet(EmpleadoMarcadorAlias::new);
        boolean nuevo = alias.getId() == null;
        alias.setEmpleadoId(request.getEmpleadoId());
        alias.setNombreMarcadorNorm(norm);
        alias.setNombreMarcadorOriginal(request.getNombreMarcador().trim());
        alias.setOrigen(origen);
        alias.setActivo(1);
        if (nuevo) {
            alias.setCreatedBy(usuario);
            alias.setCreatedAt(LocalDateTime.now());
        } else {
            alias.setUpdatedBy(usuario);
            alias.setUpdatedAt(LocalDateTime.now());
        }
        alias = aliasRepository.save(alias);

        return toDto(alias);
    }

    private MarcadorAliasDto toDto(EmpleadoMarcadorAlias alias) {
        MarcadorAliasDto dto = new MarcadorAliasDto();
        dto.setId(alias.getId());
        dto.setEmpleadoId(alias.getEmpleadoId());
        dto.setNombreMarcadorNorm(alias.getNombreMarcadorNorm());
        dto.setNombreMarcadorOriginal(alias.getNombreMarcadorOriginal());
        dto.setOrigen(alias.getOrigen());
        return dto;
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "SISTEMA";
    }
}
