package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaEmpleadoDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.dto.PersonaResumenPageDto;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.Profesion;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.ProfesionRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.GradoAcademico;
import com.indeci.rrhh.entity.Sexo;
import com.indeci.rrhh.entity.TipoDocumento;
import com.indeci.rrhh.entity.TipoPersona;

import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.GradoAcademicoRepository;
import com.indeci.rrhh.repository.SexoRepository;
import com.indeci.rrhh.repository.TipoDocumentoRepository;
import com.indeci.rrhh.repository.TipoPersonalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final AuditoriaContext auditoriaContext;
    private final SexoRepository sexoRepository;
    private final ProfesionRepository profesionRepository;
    private final GradoAcademicoRepository gradoAcademicoRepository;
    private final EstadoCivilRepository estadoCivilRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final TipoPersonalRepository tipoPersonalRepository;
    // FASE1 — régimen laboral para discriminar CAS en pantallas tributarias.
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final FtpService ftpService;

    
    
    @Transactional(readOnly = true)
    public byte[] obtenerFoto(Long personaId) {

        Persona persona =
                personaRepository
                        .findById(personaId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Persona no encontrada"));

        if(persona.getFotoPerfil() == null
                || persona.getFotoPerfil().isBlank()) {

            throw new NegocioException(
                    "La persona no tiene foto");
        }

        return ftpService.descargarArchivo(
                persona.getFotoPerfil());
    }
    
    @Transactional
    public void actualizarFoto(
            Long personaId,
            MultipartFile file) {

        Persona persona =
                personaRepository.findById(personaId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Persona no encontrada"));

        String ruta =
                ftpService.subirArchivo(
                        file,
                        "fotos-perfil",
                        file.getOriginalFilename());

        persona.setFotoPerfil(ruta);

        personaRepository.save(persona);
    }
    
    @Auditable(accion = "CREAR_PERSONA_EMPLEADO")
    public void guardar(PersonaEmpleadoDto dto) {

        // 🔥 VALIDACIONES
        if (dto.getDni() == null || dto.getDni().isBlank()) {
            auditoriaContext.setDetalle("Intento sin DNI");
            throw new NegocioException("El DNI es obligatorio");
        }
        if (!dto.getDni().matches("\\d{8}")) {
            throw new NegocioException("El DNI debe tener 8 dígitos");
        }

        if (personaRepository.existsByDni(dto.getDni())) {
            auditoriaContext.setDetalle("DNI duplicado: " + dto.getDni());
            throw new NegocioException("El DNI ya está registrado");
        }

        if (dto.getEmail() != null && personaRepository.existsByEmail(dto.getEmail())) {
            auditoriaContext.setDetalle("Email duplicado: " + dto.getEmail());
            throw new NegocioException("El email ya está registrado");
        }

        // ============================
        // PERSONA
        // ============================
        Persona persona = new Persona();
        persona.setNombreCompleto(dto.getNombreCompleto());
        persona.setDni(dto.getDni());
        
        if (dto.getEmail() != null
                && !dto.getEmail().equals(persona.getEmail())
                && personaRepository.existsByEmail(dto.getEmail())) {
            throw new NegocioException("El email ya está registrado");
        }
        
        persona.setEmail(dto.getEmail());
        persona.setTelefono(dto.getTelefono());
        persona.setDireccion(dto.getDireccion());
        persona.setDistritoId(dto.getDistritoId());
        persona.setCreatedAt(LocalDateTime.now());
        persona.setSexoId(dto.getSexoId());
        persona.setEstadoCivilId(dto.getEstadoCivilId());
        persona.setTipoDocumentoId(dto.getTipoDocumentoId());
        persona.setNacionalidad(dto.getNacionalidad());
        persona.setRuc(dto.getRuc());
        persona.setCorreoInstitucional(dto.getCorreoInstitucional());
        persona.setFotoPerfil(dto.getFotoPerfil());
        persona.setContactoEmergenciaNombre(dto.getContactoEmergenciaNombre());
        persona.setContactoEmergenciaParentesco(dto.getContactoEmergenciaParentesco());
        persona.setContactoEmergenciaTelefono(dto.getContactoEmergenciaTelefono());
        persona.setTipoPersonaId(dto.getTipoPersonalId());
        persona.setProfesionId(dto.getProfesionId());
        persona.setGradoAcademicoId(dto.getGradoAcademicoId());
        

        persona = personaRepository.save(persona);

        // ============================
        // EMPLEADO
        // ============================
        Empleado empleado = new Empleado();
        empleado.setPersonaId(persona.getId());
        empleado.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        empleado.setHasEps("N");
        empleado.setCreatedAt(LocalDateTime.now());
    
    
     
        empleado.setConadisCodigo(dto.getConadisCodigo());
        empleado.setRegistroAirhsp(dto.getRegistroAirhsp());

        empleado = empleadoRepository.save(empleado);

        // 🔥 CODIGO INTERNO SEGURO
        String codigo = String.format("EMP%05d", empleado.getId());
        empleado.setCodigoInterno(codigo);

        empleadoRepository.save(empleado);

        auditoriaContext.setDetalle("Creación persona DNI: " + dto.getDni());
    }

    @Transactional(readOnly = true)
    public List<PersonaResumenDto> listar() {
        return personaRepository.findAllResumenRaw().stream()
                .map(r -> new PersonaResumenDto(
                        toLong(r[0]),
                        toLong(r[1]),
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        (String) r[6]))
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonaResumenPageDto listarPaginado(String q, int page, int size) {
        String pattern = (q == null || q.isBlank()) ? "%" : "%" + q.trim().toUpperCase() + "%";
        long offset = (long) page * size;
        long total = personaRepository.countResumen(pattern);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        List<PersonaResumenDto> content = personaRepository.findPageResumenRaw(pattern, offset, size)
                .stream()
                .map(r -> new PersonaResumenDto(
                        toLong(r[0]), toLong(r[1]),
                        (String) r[2], (String) r[3],
                        (String) r[4], (String) r[5],
                        (String) r[6]))
                .toList();
        return new PersonaResumenPageDto(content, total, totalPages, page, size);
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof BigDecimal bd) return bd.longValue();
        if (value instanceof Number n) return n.longValue();
        return null;
    }

    // ============================
    // OBTENER
    // ============================
    public PersonaEmpleadoResponseDto obtenerPorId(Long id) {
        Persona p = personaRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Persona no encontrada"));

        Empleado emp = empleadoRepository.findByPersonaId(id).orElse(null);

        return mapearPersona(p, emp);
    }

    @Auditable(accion = "ACTUALIZAR_PERSONA")
    public void actualizar(Long id, PersonaEmpleadoDto dto) {

        Persona persona = personaRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Persona no encontrada"));

        persona.setNombreCompleto(dto.getNombreCompleto());
        persona.setEmail(dto.getEmail());
        persona.setTelefono(dto.getTelefono());
        persona.setDireccion(dto.getDireccion());
        persona.setDistritoId(dto.getDistritoId());
        persona.setSexoId(dto.getSexoId());
        persona.setEstadoCivilId(dto.getEstadoCivilId());
        persona.setTipoDocumentoId(dto.getTipoDocumentoId());
        persona.setNacionalidad(dto.getNacionalidad());
        persona.setRuc(dto.getRuc());
        persona.setCorreoInstitucional(dto.getCorreoInstitucional());
        persona.setFotoPerfil(dto.getFotoPerfil());
        persona.setContactoEmergenciaNombre(dto.getContactoEmergenciaNombre());
        persona.setContactoEmergenciaParentesco(dto.getContactoEmergenciaParentesco());
        persona.setContactoEmergenciaTelefono(dto.getContactoEmergenciaTelefono());
        persona.setProfesionId(dto.getProfesionId());
        persona.setTipoPersonaId(dto.getTipoPersonalId());
        persona.setGradoAcademicoId(dto.getGradoAcademicoId());

        personaRepository.save(persona);

        Empleado emp = empleadoRepository.findByPersonaId(id)
                .orElseThrow(() -> new NegocioException("Empleado no encontrado"));

        emp.setEstado(dto.getEstado());
  
     
        emp.setConadisCodigo(dto.getConadisCodigo());
     
        emp.setRegistroAirhsp(dto.getRegistroAirhsp());
        
        empleadoRepository.save(emp);

        auditoriaContext.setDetalle("Actualización persona ID: " + id);
    }

    // ============================
    // ELIMINAR
    // ============================
    @Auditable(accion = "ELIMINAR_PERSONA")
    public void eliminar(Long id) {

        Empleado emp = empleadoRepository.findByPersonaId(id)
                .orElseThrow(() -> new NegocioException("Empleado no encontrado"));

        emp.setEstado("INACTIVO");

        empleadoRepository.save(emp);

        auditoriaContext.setDetalle("Eliminación lógica persona ID: " + id);
    }

    private PersonaEmpleadoResponseDto mapearPersona(Persona p, Empleado emp) {

        PersonaEmpleadoResponseDto dto = new PersonaEmpleadoResponseDto();

        dto.setId(p.getId());
        dto.setNombreCompleto(p.getNombreCompleto());
        dto.setDni(p.getDni());
        dto.setEmail(p.getEmail());
        dto.setTelefono(p.getTelefono());
        dto.setDireccion(p.getDireccion());
        dto.setDistritoId(p.getDistritoId());
        dto.setContactoEmergenciaNombre(p.getContactoEmergenciaNombre());
        dto.setContactoEmergenciaParentesco(p.getContactoEmergenciaParentesco());
        dto.setContactoEmergenciaTelefono(p.getContactoEmergenciaTelefono());
        dto.setSexoId(p.getSexoId());
        dto.setEstadoCivilId(p.getEstadoCivilId());
        dto.setTipoDocumentoId(p.getTipoDocumentoId());
        dto.setNacionalidad(p.getNacionalidad());
        dto.setRuc(p.getRuc());
        dto.setCorreoInstitucional(p.getCorreoInstitucional());
        dto.setFotoPerfil(p.getFotoPerfil());
        if (emp != null) {
            dto.setRegistroAirhsp(emp.getRegistroAirhsp());
        }

        // ======================================
        // CATALOGOS
        // ======================================

        if (p.getSexoId() != null) {
            Sexo sexo = sexoRepository.findById(p.getSexoId()).orElse(null);
            if (sexo != null) {
                dto.setSexo(sexo.getNombre());
            }
        }

        if (p.getEstadoCivilId() != null) {
            EstadoCivil estadoCivil = estadoCivilRepository.findById(p.getEstadoCivilId()).orElse(null);
            if (estadoCivil != null) {
                dto.setEstadoCivil(estadoCivil.getNombre());
            }
        }

        if (p.getTipoDocumentoId() != null) {
            TipoDocumento tipoDocumento = tipoDocumentoRepository.findById(p.getTipoDocumentoId()).orElse(null);
            if (tipoDocumento != null) {
                dto.setTipoDocumento(tipoDocumento.getNombre());
            }
        }

        if (emp != null) {
            dto.setEmpleadoId(emp.getId());
            dto.setCodigoInterno(emp.getCodigoInterno());
            dto.setEstado(emp.getEstado());
            dto.setProfesionId(p.getProfesionId());
            dto.setGradoAcademicoId(p.getGradoAcademicoId());
            dto.setRegistroAirhsp(
                    emp.getRegistroAirhsp());

            
            if (p.getProfesionId() != null) {
                Profesion profesion = profesionRepository.findById(p.getProfesionId()).orElse(null);
                if (profesion != null) {
                    dto.setProfesion(profesion.getNombre());
                }
            }

            if (p.getGradoAcademicoId() != null) {
                GradoAcademico grado = gradoAcademicoRepository.findById(p.getGradoAcademicoId()).orElse(null);
                if (grado != null) {
                    dto.setGradoAcademico(grado.getNombre());
                }
            }

            dto.setConadisCodigo(emp.getConadisCodigo());
            dto.setTipoPersonalId(p.getTipoPersonaId());

            if (p.getTipoPersonaId() != null) {
                TipoPersona tipoPersonal = tipoPersonalRepository.findById(p.getTipoPersonaId()).orElse(null);
                if (tipoPersonal != null) {
                    dto.setTipoPersonal(tipoPersonal.getNombre());
                }
            }

            // FASE1 — régimen laboral vigente (de la config de planilla activa).
            empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(emp.getId(), 1)
                    .map(EmpleadoPlanilla::getRegimenLaboralId)
                    .filter(java.util.Objects::nonNull)
                    .flatMap(regimenLaboralRepository::findById)
                    .ifPresent(rl -> dto.setRegimenLaboral(rl.getCodigo()));
        }

        return dto;
    }
}