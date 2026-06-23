package com.indeci.rrhh.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.LegajoDocumentoDto;
import com.indeci.rrhh.dto.LegajoDocumentoResponseDto;
import com.indeci.rrhh.entity.Capacitacion;
import com.indeci.rrhh.entity.ConocimientoInformatico;
import com.indeci.rrhh.entity.ExperienciaLaboral;
import com.indeci.rrhh.entity.FormacionAcademica;
import com.indeci.rrhh.entity.Idioma;
import com.indeci.rrhh.entity.MedidaDisciplinaria;
import com.indeci.rrhh.entity.Reconocimiento;
import com.indeci.rrhh.repository.CapacitacionRepository;
import com.indeci.rrhh.repository.ConocimientoInformaticoRepository;
import com.indeci.rrhh.repository.ExperienciaLaboralRepository;
import com.indeci.rrhh.repository.FormacionAcademicaRepository;
import com.indeci.rrhh.repository.IdiomaRepository;
import com.indeci.rrhh.repository.MedidaDisciplinariaRepository;
import com.indeci.rrhh.repository.ReconocimientoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LegajoSustentoService {

    private final LegajoDocumentoService legajoDocumentoService;

    private final FormacionAcademicaRepository formacionRepository;
    private final CapacitacionRepository capacitacionRepository;
    private final IdiomaRepository idiomaRepository;
    private final ConocimientoInformaticoRepository conocimientoRepository;
    private final ExperienciaLaboralRepository experienciaRepository;
    private final ReconocimientoRepository reconocimientoRepository;
    private final MedidaDisciplinariaRepository medidaRepository;

    @Transactional
    public LegajoDocumentoResponseDto reemplazarSustento(
            String tipo,
            Long registroId,
            Long empleadoId,
            Long categoriaId,
            Long subcategoriaId,
            String nombreDocumento,
            LocalDate fechaDocumento,
            String observacion,
            MultipartFile file) {

        validarDatosBasicos(tipo, registroId, empleadoId, categoriaId, file);

        String tipoNormalizado = tipo.trim().toUpperCase();

        // 1. PRIMERO capturamos el documento anterior
        Long documentoAnteriorId = obtenerDocumentoActual(tipoNormalizado, registroId);

        System.out.println("DOCUMENTO ANTERIOR ID: " + documentoAnteriorId);

        // 2. Subimos el nuevo documento
        LegajoDocumentoDto dto = new LegajoDocumentoDto();
        dto.setEmpleadoId(empleadoId);
        dto.setCategoriaId(categoriaId);
        dto.setSubcategoriaId(subcategoriaId);
        dto.setNombreDocumento(nombreDocumento);
        dto.setFechaDocumento(fechaDocumento);
        dto.setObservacion(observacion);
        dto.setOrigen("LEGAJO");
        dto.setReferenciaId(registroId);

        LegajoDocumentoResponseDto nuevoDocumento =
                legajoDocumentoService.subir(dto, file);

        System.out.println("DOCUMENTO NUEVO ID: " + nuevoDocumento.getId());

        // 3. Vinculamos el nuevo documento al registro
        vincularNuevoDocumento(
                tipoNormalizado,
                registroId,
                nuevoDocumento.getId());

        // 4. Recién aquí inactivamos el anterior
        if (documentoAnteriorId != null
                && !documentoAnteriorId.equals(nuevoDocumento.getId())) {

            legajoDocumentoService.inactivarSiExiste(documentoAnteriorId);

            System.out.println("DOCUMENTO ANTERIOR INACTIVADO: " + documentoAnteriorId);
        }

        return nuevoDocumento;
    }

    private void validarDatosBasicos(
            String tipo,
            Long registroId,
            Long empleadoId,
            Long categoriaId,
            MultipartFile file) {

        if (tipo == null || tipo.isBlank()) {
            throw new NegocioException("Tipo de registro requerido");
        }

        if (registroId == null) {
            throw new NegocioException("registroId requerido");
        }

        if (empleadoId == null) {
            throw new NegocioException("empleadoId requerido");
        }

        if (categoriaId == null) {
            throw new NegocioException("categoriaId requerido");
        }

        if (file == null || file.isEmpty()) {
            throw new NegocioException("Archivo requerido");
        }
    }

    private Long obtenerDocumentoActual(
            String tipo,
            Long registroId) {

        return switch (tipo) {
            case "FORMACION" -> {
                FormacionAcademica entity =
                        formacionRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Formación académica no encontrada"));
                yield entity.getLegajoDocumentoId();
            }

            case "CAPACITACION" -> {
                Capacitacion entity =
                        capacitacionRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Capacitación no encontrada"));
                yield entity.getLegajoDocumentoId();
            }

            case "IDIOMA" -> {
                Idioma entity =
                        idiomaRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Idioma no encontrado"));
                yield entity.getLegajoDocumentoId();
            }

            case "CONOCIMIENTO" -> {
                ConocimientoInformatico entity =
                        conocimientoRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Conocimiento informático no encontrado"));
                yield entity.getLegajoDocumentoId();
            }

            case "EXPERIENCIA" -> {
                ExperienciaLaboral entity =
                        experienciaRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Experiencia laboral no encontrada"));
                yield entity.getLegajoDocumentoId();
            }

            case "RECONOCIMIENTO" -> {
                Reconocimiento entity =
                        reconocimientoRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Reconocimiento no encontrado"));
                yield entity.getLegajoDocumentoId();
            }

            case "MEDIDA" -> {
                MedidaDisciplinaria entity =
                        medidaRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Medida disciplinaria no encontrada"));
                yield entity.getLegajoDocumentoId();
            }

            default -> throw new NegocioException(
                    "Tipo de registro no soportado: " + tipo);
        };
    }

    private void vincularNuevoDocumento(
            String tipo,
            Long registroId,
            Long nuevoDocumentoId) {

        switch (tipo) {
            case "FORMACION" -> {
                FormacionAcademica entity =
                        formacionRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Formación académica no encontrada"));
                entity.setLegajoDocumentoId(nuevoDocumentoId);
                formacionRepository.save(entity);
            }

            case "CAPACITACION" -> {
                Capacitacion entity =
                        capacitacionRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Capacitación no encontrada"));
                entity.setLegajoDocumentoId(nuevoDocumentoId);
                capacitacionRepository.save(entity);
            }

            case "IDIOMA" -> {
                Idioma entity =
                        idiomaRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Idioma no encontrado"));
                entity.setLegajoDocumentoId(nuevoDocumentoId);
                idiomaRepository.save(entity);
            }

            case "CONOCIMIENTO" -> {
                ConocimientoInformatico entity =
                        conocimientoRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Conocimiento informático no encontrado"));
                entity.setLegajoDocumentoId(nuevoDocumentoId);
                conocimientoRepository.save(entity);
            }

            case "EXPERIENCIA" -> {
                ExperienciaLaboral entity =
                        experienciaRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Experiencia laboral no encontrada"));
                entity.setLegajoDocumentoId(nuevoDocumentoId);
                experienciaRepository.save(entity);
            }

            case "RECONOCIMIENTO" -> {
                Reconocimiento entity =
                        reconocimientoRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Reconocimiento no encontrado"));
                entity.setLegajoDocumentoId(nuevoDocumentoId);
                reconocimientoRepository.save(entity);
            }

            case "MEDIDA" -> {
                MedidaDisciplinaria entity =
                        medidaRepository.findById(registroId)
                                .orElseThrow(() ->
                                        new NegocioException(
                                                "Medida disciplinaria no encontrada"));
                entity.setLegajoDocumentoId(nuevoDocumentoId);
                medidaRepository.save(entity);
            }

            default -> throw new NegocioException(
                    "Tipo de registro no soportado: " + tipo);
        }
    }
}