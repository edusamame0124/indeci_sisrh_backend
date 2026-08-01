package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDocDto;
import com.indeci.rrhh.dto.SolicitudRrhhDocResponseDto;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.SolicitudRrhhDoc;
import com.indeci.rrhh.repository.SolicitudRrhhDocRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SolicitudRrhhDocService {

    private final SolicitudRrhhDocRepository
            repository;

    private final SolicitudRrhhRepository
            solicitudRepository;

    // ==========================================
    // SUBIR DOCUMENTO
    // ==========================================

    public void registrar(
            SolicitudRrhhDocDto dto) {

        solicitudRepository
                .findById(dto.getSolicitudId())
                .orElseThrow(() ->
                        new NegocioException(
                                "Solicitud no encontrada"));

        SolicitudRrhhDoc entity =
                new SolicitudRrhhDoc();

        entity.setSolicitudId(
                dto.getSolicitudId());

        entity.setEtapa(
                dto.getEtapa());

        entity.setNombreArchivo(
                dto.getNombreArchivo());

        entity.setRutaArchivo(
                dto.getRutaArchivo());

        entity.setVersionDoc(
                dto.getVersionDoc());

        entity.setObservacion(
                dto.getObservacion());

        entity.setUsuarioUpload(
                "ADMIN");

        entity.setCreatedAt(
                LocalDateTime.now());

        entity.setActivo(1);

        repository.save(entity);
    }

    // ==========================================
    // LISTAR DOCUMENTOS
    // ==========================================

    /**
     * Defensa en profundidad (IDOR): el controller acepta PAP_EMPLEADO además de los permisos
     * de revisor/admin (PAP_JEFE/PAP_RRHH/PAP_APROBAR_RRHH/EMP_READ/EMP_WRITE). Quien SOLO
     * tiene PAP_EMPLEADO (empleado raso) no debe poder leer documentos de una papeleta ajena
     * pasando cualquier {@code solicitudId} — se valida que la papeleta le pertenezca. Los
     * roles revisores/admin conservan el acceso sin restricción (revisan papeletas ajenas por
     * diseño).
     */
    public List<SolicitudRrhhDocResponseDto>
    listar(Long solicitudId) {

        if (!com.indeci.security.util.SecurityUtil.hasAnyAuthority(
                "PAP_JEFE", "PAP_RRHH", "PAP_APROBAR_RRHH",
                "EMP_READ", "EMP_WRITE", "ROLE_SUPER_ADMIN")) {

            SolicitudRrhh solicitud = solicitudRepository
                    .findById(solicitudId)
                    .orElseThrow(() -> new NegocioException("Solicitud no encontrada"));

            Long empleadoId = com.indeci.security.util.SecurityUtil.getEmpleadoId();

            if (!java.util.Objects.equals(solicitud.getEmpleadoId(), empleadoId)) {
                throw new NegocioException(
                        "No puede ver documentos de una papeleta de otro empleado");
            }
        }

        return repository
                .findBySolicitudIdAndActivoOrderByVersionDocAsc(
                        solicitudId,
                        1)
                .stream()
                .map(this::convertir)
                .toList();
    }

    private SolicitudRrhhDocResponseDto
    convertir(SolicitudRrhhDoc d) {

        SolicitudRrhhDocResponseDto dto =
                new SolicitudRrhhDocResponseDto();

        dto.setId(d.getId());

        dto.setSolicitudId(
                d.getSolicitudId());

        dto.setEtapa(
                d.getEtapa());

        dto.setNombreArchivo(
                d.getNombreArchivo());

        dto.setRutaArchivo(
                d.getRutaArchivo());

        dto.setVersionDoc(
                d.getVersionDoc());

        dto.setObservacion(
                d.getObservacion());

        dto.setUsuarioUpload(
                d.getUsuarioUpload());

        dto.setCreatedAt(
                d.getCreatedAt());

        return dto;
    }
}