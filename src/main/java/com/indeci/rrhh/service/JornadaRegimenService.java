package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.JornadaRegimenDto;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configuración de jornada y tolerancias por régimen laboral (M04).
 * 1 configuración por régimen (upsert). Las horas se validan en formato HH:mm.
 */
@Service
@RequiredArgsConstructor
public class JornadaRegimenService {

    private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final JornadaRegimenRepository jornadaRepository;
    private final RegimenLaboralRepository regimenRepository;

    /** Lista todas las jornadas configuradas (una por régimen), con código/nombre del régimen. */
    @Transactional(readOnly = true)
    public List<JornadaRegimenDto> listar() {
        Map<Long, RegimenLaboral> regimenes = regimenRepository.findAll().stream()
                .collect(Collectors.toMap(RegimenLaboral::getId, Function.identity()));
        return jornadaRepository.findAll().stream()
                .map(j -> toDto(j, regimenes.get(j.getRegimenLaboralId())))
                .sorted((a, b) -> {
                    String ca = a.getRegimenCodigo() != null ? a.getRegimenCodigo() : "";
                    String cb = b.getRegimenCodigo() != null ? b.getRegimenCodigo() : "";
                    return ca.compareTo(cb);
                })
                .toList();
    }

    /** Devuelve la configuración del régimen, o una con valores por defecto si no existe. */
    @Transactional(readOnly = true)
    public JornadaRegimenDto obtener(Long regimenLaboralId) {
        RegimenLaboral regimen = regimenRepository.findById(regimenLaboralId)
                .orElseThrow(() -> new NegocioException("Régimen laboral no encontrado."));

        return jornadaRepository.findByRegimenLaboralId(regimenLaboralId)
                .map(j -> toDto(j, regimen))
                .orElseGet(() -> {
                    JornadaRegimenDto dto = new JornadaRegimenDto();
                    dto.setRegimenLaboralId(regimenLaboralId);
                    dto.setRegimenCodigo(regimen.getCodigo());
                    dto.setRegimenNombre(regimen.getNombre());
                    dto.setToleranciaIngresoMin(0);
                    dto.setToleranciaAlmuerzoMin(0);
                    dto.setUmbralTardanzaDiariaMin(10);
                    dto.setTopeTardanzaMensualMin(60);
                    dto.setJornadaHoras(BigDecimal.valueOf(8));
                    return dto;
                });
    }

    @Auditable(accion = "ELIMINAR_JORNADA_REGIMEN")
    @Transactional
    public void eliminar(Long regimenLaboralId) {
        JornadaRegimen entity = jornadaRepository.findByRegimenLaboralId(regimenLaboralId)
                .orElseThrow(() -> new NegocioException(
                        "No existe configuración de jornada para el régimen."));
        jornadaRepository.delete(entity);
    }

    @Auditable(accion = "GUARDAR_JORNADA_REGIMEN")
    @Transactional
    public JornadaRegimenDto guardar(JornadaRegimenDto dto) {
        if (dto == null || dto.getRegimenLaboralId() == null) {
            throw new NegocioException("Debe seleccionar un régimen laboral.");
        }
        RegimenLaboral regimen = regimenRepository.findById(dto.getRegimenLaboralId())
                .orElseThrow(() -> new NegocioException("Régimen laboral no encontrado."));

        validar(dto);

        JornadaRegimen entity = jornadaRepository.findByRegimenLaboralId(dto.getRegimenLaboralId())
                .orElseGet(() -> {
                    JornadaRegimen nueva = new JornadaRegimen();
                    nueva.setRegimenLaboralId(dto.getRegimenLaboralId());
                    nueva.setActivo(1);
                    nueva.setCreatedAt(LocalDateTime.now());
                    return nueva;
                });

        entity.setHoraIngreso(trim(dto.getHoraIngreso()));
        entity.setHoraSalida(trim(dto.getHoraSalida()));
        entity.setRefrigerioInicio(trim(dto.getRefrigerioInicio()));
        entity.setRefrigerioFin(trim(dto.getRefrigerioFin()));
        entity.setToleranciaIngresoMin(dto.getToleranciaIngresoMin() != null ? dto.getToleranciaIngresoMin() : 0);
        entity.setToleranciaAlmuerzoMin(dto.getToleranciaAlmuerzoMin() != null ? dto.getToleranciaAlmuerzoMin() : 0);
        entity.setUmbralTardanzaDiariaMin(dto.getUmbralTardanzaDiariaMin() != null ? dto.getUmbralTardanzaDiariaMin() : 10);
        entity.setTopeTardanzaMensualMin(dto.getTopeTardanzaMensualMin() != null ? dto.getTopeTardanzaMensualMin() : 60);
        entity.setJornadaHoras(dto.getJornadaHoras() != null ? dto.getJornadaHoras() : BigDecimal.valueOf(8));
        entity.setUpdatedAt(LocalDateTime.now());

        return toDto(jornadaRepository.save(entity), regimen);
    }

    private void validar(JornadaRegimenDto dto) {
        // Obligatorios.
        if (esVacio(dto.getHoraIngreso())) {
            throw new NegocioException("La hora de ingreso es obligatoria.");
        }
        if (esVacio(dto.getHoraSalida())) {
            throw new NegocioException("La hora de salida es obligatoria.");
        }
        if (dto.getToleranciaIngresoMin() == null) {
            throw new NegocioException("La tolerancia de ingreso es obligatoria.");
        }

        validarHora(dto.getHoraIngreso(), "hora de ingreso");
        validarHora(dto.getHoraSalida(), "hora de salida");
        validarHora(dto.getRefrigerioInicio(), "inicio de refrigerio");
        validarHora(dto.getRefrigerioFin(), "fin de refrigerio");

        if (esMayor(dto.getHoraIngreso(), dto.getHoraSalida())) {
            throw new NegocioException("La hora de salida debe ser posterior a la de ingreso.");
        }
        if (esMayor(dto.getRefrigerioInicio(), dto.getRefrigerioFin())) {
            throw new NegocioException("El fin de refrigerio debe ser posterior al inicio.");
        }
        if (dto.getToleranciaIngresoMin() != null && dto.getToleranciaIngresoMin() < 0) {
            throw new NegocioException("La tolerancia de ingreso no puede ser negativa.");
        }
        if (dto.getToleranciaAlmuerzoMin() != null && dto.getToleranciaAlmuerzoMin() < 0) {
            throw new NegocioException("La tolerancia de regreso de almuerzo no puede ser negativa.");
        }
        if (dto.getJornadaHoras() != null && dto.getJornadaHoras().signum() <= 0) {
            throw new NegocioException("La jornada (horas) debe ser mayor que cero.");
        }
        if (dto.getUmbralTardanzaDiariaMin() != null && dto.getUmbralTardanzaDiariaMin() < 0) {
            throw new NegocioException("El umbral de tardanza diaria no puede ser negativo.");
        }
        if (dto.getTopeTardanzaMensualMin() != null && dto.getTopeTardanzaMensualMin() < 0) {
            throw new NegocioException("El tope mensual de tardanzas no puede ser negativo.");
        }
    }

    private void validarHora(String hora, String campo) {
        if (hora != null && !hora.isBlank() && !HORA.matcher(hora.trim()).matches()) {
            throw new NegocioException("Formato inválido en " + campo + " (use HH:mm).");
        }
    }

    /** true si ambas horas existen y la primera es >= la segunda. */
    private boolean esMayor(String a, String b) {
        Integer ma = toMinutos(a);
        Integer mb = toMinutos(b);
        return ma != null && mb != null && ma >= mb;
    }

    private Integer toMinutos(String hora) {
        if (hora == null || hora.isBlank() || !HORA.matcher(hora.trim()).matches()) {
            return null;
        }
        String[] p = hora.trim().split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private String trim(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    private JornadaRegimenDto toDto(JornadaRegimen j, RegimenLaboral regimen) {
        JornadaRegimenDto dto = new JornadaRegimenDto();
        dto.setId(j.getId());
        dto.setRegimenLaboralId(j.getRegimenLaboralId());
        dto.setRegimenCodigo(regimen != null ? regimen.getCodigo() : null);
        dto.setRegimenNombre(regimen != null ? regimen.getNombre() : null);
        dto.setHoraIngreso(j.getHoraIngreso());
        dto.setHoraSalida(j.getHoraSalida());
        dto.setRefrigerioInicio(j.getRefrigerioInicio());
        dto.setRefrigerioFin(j.getRefrigerioFin());
        dto.setToleranciaIngresoMin(j.getToleranciaIngresoMin());
        dto.setToleranciaAlmuerzoMin(j.getToleranciaAlmuerzoMin());
        dto.setUmbralTardanzaDiariaMin(j.getUmbralTardanzaDiariaMin());
        dto.setTopeTardanzaMensualMin(j.getTopeTardanzaMensualMin());
        dto.setJornadaHoras(j.getJornadaHoras());
        return dto;
    }
}
