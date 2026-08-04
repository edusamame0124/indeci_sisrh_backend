package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDocDto;
import com.indeci.rrhh.dto.SolicitudRrhhDocResponseDto;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.SolicitudRrhhDoc;
import com.indeci.rrhh.repository.SolicitudRrhhDocRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SolicitudRrhhDocService {

    private static final Set<String> ETAPAS_SUBSANABLES = Set.of("JEFE", "RRHH");

    private final SolicitudRrhhDocRepository
            repository;

    private final SolicitudRrhhRepository
            solicitudRepository;

    private final FtpService
            ftpService;

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
    // SUBSANAR DOCUMENTO (remediación de aprobaciones
    // aceptadas sin la papeleta firmada real adjunta)
    // ==========================================

    /**
     * Agrega el PDF real como una nueva versión del documento de la etapa indicada,
     * SIN reabrir ni cambiar el estado de la solicitud. Uso: corregir solicitudes
     * cuyo documento de JEFE/RRHH quedó con el PDF fabricado por el sistema
     * ("solicitud_no_requiere_firma.pdf") porque en su momento se aprobó sin adjuntar
     * archivo. Restringido a las etapas de aprobación (JEFE/RRHH); la etapa EMPLEADO
     * no aplica porque ahí el PDF sin firma es un comportamiento normativo válido, no un bug.
     */
    public void subsanar(
            Long solicitudId,
            String etapa,
            MultipartFile file,
            String observacion) {

        if (!ETAPAS_SUBSANABLES.contains(etapa)) {
            throw new NegocioException(
                    "Solo se puede subsanar el documento de las etapas JEFE o RRHH.");
        }

        if (file == null || file.isEmpty()) {
            throw new NegocioException(
                    "Debe adjuntar el PDF real de la papeleta firmada.");
        }

        solicitudRepository
                .findById(solicitudId)
                .orElseThrow(() ->
                        new NegocioException(
                                "Solicitud no encontrada"));

        int siguienteVersion =
                repository
                        .findTopBySolicitudIdOrderByVersionDocDesc(solicitudId)
                        .map(SolicitudRrhhDoc::getVersionDoc)
                        .orElse(0)
                        + 1;

        String rutaArchivo =
                ftpService.subirArchivo(
                        file,
                        "papeletas",
                        file.getOriginalFilename());

        SolicitudRrhhDoc doc = new SolicitudRrhhDoc();

        doc.setSolicitudId(solicitudId);
        doc.setEtapa(etapa);
        doc.setNombreArchivo(file.getOriginalFilename());
        doc.setRutaArchivo(rutaArchivo);
        doc.setMimeType(file.getContentType());
        doc.setTamanioBytes(file.getSize());
        doc.setVersionDoc(siguienteVersion);
        doc.setObservacion(
                (observacion == null || observacion.isBlank())
                        ? "Subsanación: documento real reemplaza al generado automáticamente "
                                + "por el sistema por aprobación sin adjunto."
                        : observacion);
        doc.setUsuarioUpload(SecurityUtil.getUsername());
        doc.setCreatedAt(LocalDateTime.now());
        doc.setActivo(1);

        repository.save(doc);
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