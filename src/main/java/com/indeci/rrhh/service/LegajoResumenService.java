package com.indeci.rrhh.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.LegajoResumenDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LegajoResumenService {

    private final PersonaService personaService;

    private final EmpleadoRepository empleadoRepository;

    private final FormacionAcademicaService
            formacionAcademicaService;

    private final CapacitacionService
            capacitacionService;

    private final IdiomaService
            idiomaService;

    private final ConocimientoInformaticoService
            conocimientoInformaticoService;

    private final FamiliarService
            familiarService;

    private final ExperienciaLaboralService
            experienciaLaboralExternaService;

    private final ReconocimientoService
            reconocimientoService;

    private final MedidaDisciplinariaService
            medidaDisciplinariaService;

    private final FtpService
            ftpService;

    /** Autoservicio — resuelve la persona del empleado autenticado (claim {@code empleadoId} del JWT). */
    @Transactional(readOnly = true)
    public LegajoResumenDto obtenerPropio() {

        Long empleadoId = SecurityUtil.getEmpleadoId();

        if (empleadoId == null) {
            throw new NegocioException(
                    "Su usuario no tiene un empleado vinculado. Contacte al administrador.");
        }

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new NegocioException(
                        "No se encontró el empleado vinculado a su usuario."));

        // Autoservicio: sin foto acá — el frontend la trae de /persona/me/foto
        // (mismo endpoint que Mi Perfil). Evita pagar el round-trip FTP dentro
        // de este endpoint agregado, que ya hace 8 consultas más.
        return obtener(empleado.getPersonaId(), false);
    }

    /** Operativo RRHH — mantiene la foto embebida (comportamiento histórico, sin cambios). */
    @Transactional(readOnly = true)
    public LegajoResumenDto obtener(
            Long personaId) {
        return obtener(personaId, true);
    }

    private LegajoResumenDto obtener(
            Long personaId,
            boolean incluirFoto) {

        PersonaEmpleadoResponseDto persona =
                personaService.obtenerPorId(
                        personaId);

        LegajoResumenDto dto =
                new LegajoResumenDto();

        dto.setPersona(persona);

        // FOTO
        if(incluirFoto
                && persona.getFotoPerfil() != null
                && !persona.getFotoPerfil().isBlank()) {

            try {

                dto.setFotoPerfil(
                        ftpService.descargarArchivo(
                                persona.getFotoPerfil()));

            } catch (Exception ex) {

                dto.setFotoPerfil(null);
            }
        }

        Long empleadoId =
                persona.getEmpleadoId();

        dto.setFormacionAcademica(
                formacionAcademicaService
                        .listarPorEmpleado(
                                empleadoId));

        dto.setCapacitaciones(
                capacitacionService
                        .listarPorEmpleado(
                                empleadoId));

        dto.setIdiomas(
                idiomaService
                        .listarPorEmpleado(
                                empleadoId));

        dto.setConocimientosInformaticos(
                conocimientoInformaticoService
                        .listarPorEmpleado(
                                empleadoId));

        dto.setFamiliares(
                familiarService
                        .listarPorEmpleado(
                                empleadoId));

        dto.setExperienciaLaboralExterna(
                experienciaLaboralExternaService
                        .listarPorEmpleado(
                                empleadoId));

        dto.setReconocimientos(
                reconocimientoService
                        .listarPorEmpleado(
                                empleadoId));

        dto.setMedidasDisciplinarias(
                medidaDisciplinariaService
                        .listarPorEmpleado(
                                empleadoId));

        return dto;
    }
}