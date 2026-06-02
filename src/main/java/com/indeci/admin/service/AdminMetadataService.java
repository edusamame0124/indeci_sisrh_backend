package com.indeci.admin.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.admin.dto.AdminPermisoResponse;
import com.indeci.admin.dto.AdminRolResponse;
import com.indeci.user.entity.Permiso;
import com.indeci.user.entity.Rol;
import com.indeci.user.repository.PermisoRepository;
import com.indeci.user.repository.RolPermisoRepository;
import com.indeci.user.repository.RolRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminMetadataService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final RolPermisoRepository rolPermisoRepository;

    @Transactional(readOnly = true)
    public List<AdminRolResponse> listRoles() {
        return rolRepository.findAll().stream()
                .filter(r -> r.getActivo() == null || "S".equalsIgnoreCase(r.getActivo()))
                .sorted(Comparator.comparing(Rol::getCodigo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(r -> new AdminRolResponse(r.getId(), r.getCodigo(), r.getNombre(), r.getActivo(), r.getNivel()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPermisoResponse> listPermisosDeRol(Long rolId) {
        return rolPermisoRepository.findByRolId(rolId).stream()
                .map(rp -> permisoRepository.findById(rp.getPermisoId()).orElse(null))
                .filter(p -> p != null && ("S".equalsIgnoreCase(p.getActivo()) || p.getActivo() == null))
                .sorted(Comparator.comparing(Permiso::getCodigo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(p -> new AdminPermisoResponse(p.getId(), p.getCodigo(), p.getDescripcion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPermisoResponse> listPermisos() {
        return permisoRepository.findAll().stream()
                .filter(p -> p.getActivo() == null || "S".equalsIgnoreCase(p.getActivo()))
                .sorted(Comparator.comparing(Permiso::getCodigo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(p -> new AdminPermisoResponse(p.getId(), p.getCodigo(), p.getDescripcion()))
                .toList();
    }
}
