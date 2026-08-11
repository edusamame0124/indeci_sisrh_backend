package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiariaRowDto;
import com.indeci.rrhh.dto.AsistenciaMarcacionRowDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.service.AsistenciaService;
import com.indeci.rrhh.service.PersonaService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reporte de marcaciones diarias (1 fila por día x empleado), formato
 * institucional {@code docs/EJEMPLO REPORTE EXCEL SISTEMA.xlsx}. Reutiliza
 * {@link AsistenciaService#listarDiaria} — misma consulta ya enriquecida
 * (papeleta/teletrabajo/horario especial) que usa la pantalla — y agrega en
 * batch los campos que esa proyección no trae (régimen, oficina, día de
 * semana, horario de salida programado).
 *
 * <p>Decisión RR.HH. (2026-08-11): "Horas extras del día" replica la resta
 * simple del Excel de referencia (horario − marcación de entrada + marcación
 * − horario de salida), NO el cómputo normativo 25/35/100% que ya usa el
 * motor de planilla (ese sigue siendo la fuente para el cálculo de haberes;
 * esta columna es solo informativa para RR.HH. en este reporte).</p>
 */
@Service
@RequiredArgsConstructor
public class AsistenciaMarcacionesXlsxService {

    private static final DateTimeFormatter[] FORMATOS_HORA = {
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("H:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("H:mm", Locale.ROOT),
    };
    private final AsistenciaService asistenciaService;
    private final AsistenciaDetalleRepository detalleRepository;
    private final PersonaService personaService;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final JornadaRegimenRepository jornadaRegimenRepository;
    private final EmpleadoPuestoRepository empleadoPuestoRepository;

    @Transactional(readOnly = true)
    public List<AsistenciaMarcacionRowDto> generar(LocalDate fechaInicio, LocalDate fechaFin) {
        List<AsistenciaDiariaRowDto> filas = asistenciaService
                .listarDiaria(fechaInicio, fechaFin, null, null, false, null, Pageable.unpaged())
                .getContent();
        if (filas.isEmpty()) {
            return List.of();
        }

        List<Long> empleadoIds = filas.stream()
                .map(AsistenciaDiariaRowDto::getEmpleadoId)
                .distinct()
                .toList();
        List<Long> detalleIds = filas.stream()
                .map(AsistenciaDiariaRowDto::getDetalleId)
                .toList();

        Map<Long, PersonaResumenDto> personasPorEmpleado = personaService.listar().stream()
                .collect(Collectors.toMap(PersonaResumenDto::getEmpleadoId, Function.identity(), (a, b) -> a));
        Map<Long, String> diaSemanaPorDetalle = detalleRepository.findAllById(detalleIds).stream()
                .collect(Collectors.toMap(AsistenciaDetalle::getId, d -> valor(d.getDiaSemana())));
        Map<Long, List<EmpleadoPuesto>> puestosPorEmpleado = empleadoPuestoRepository
                .findByEmpleadoIdIn(empleadoIds).stream()
                .collect(Collectors.groupingBy(EmpleadoPuesto::getEmpleadoId));
        Map<Long, JornadaRegimen> jornadaPorEmpleado = new HashMap<>();

        List<AsistenciaMarcacionRowDto> resultado = new ArrayList<>(filas.size());
        for (AsistenciaDiariaRowDto fila : filas) {
            resultado.add(construirFila(
                    fila,
                    personasPorEmpleado.get(fila.getEmpleadoId()),
                    diaSemanaPorDetalle.get(fila.getDetalleId()),
                    oficinaVigente(puestosPorEmpleado.get(fila.getEmpleadoId()), fila.getFecha()),
                    jornadaPorEmpleado.computeIfAbsent(fila.getEmpleadoId(), this::jornadaDe)));
        }
        return resultado;
    }

    private AsistenciaMarcacionRowDto construirFila(
            AsistenciaDiariaRowDto fila, PersonaResumenDto persona, String diaSemana,
            EmpleadoPuesto puesto, JornadaRegimen jornada) {

        AsistenciaMarcacionRowDto row = new AsistenciaMarcacionRowDto();
        row.setDni(fila.getDni());
        row.setNombreCompleto(fila.getNombreCompleto());
        row.setRegimen(persona != null ? persona.getRegimenLaboral() : null);
        row.setUnidadOrganica(direccionUnidadDe(puesto));
        row.setDiaSemana(diaSemana);
        row.setFecha(fila.getFecha());

        boolean horarioEspecial = fila.isTieneHorarioEspecial();
        String horarioEntrada = horarioEspecial && fila.getHorarioEspecialIngreso() != null
                ? fila.getHorarioEspecialIngreso()
                : primero(fila.getHoraEntradaEsperada(), jornada != null ? jornada.getHoraIngreso() : null);
        String horarioSalida = horarioEspecial && fila.getHorarioEspecialSalida() != null
                ? fila.getHorarioEspecialSalida()
                : (jornada != null ? jornada.getHoraSalida() : null);
        row.setHorarioEntrada(horarioEntrada);
        row.setHorarioSalida(horarioSalida);
        row.setMarcaEntrada(fila.getMarcaEntrada());
        row.setMarcaSalida(fila.getMarcaSalida());
        row.setHorasExtraDiaMin(horasExtraDiaSimple(
                horarioEntrada, fila.getMarcaEntrada(), fila.getMarcaSalida(), horarioSalida));
        row.setMinutosSalidaAnticipada(minutosSalidaAnticipada(horarioSalida, fila.getMarcaSalida()));

        row.setCondicion(TipoDiaAsistencia.etiqueta(fila.getTipoDia()));
        row.setPermisoPapeletaTeletrabajo(permisoPapeletaTeletrabajo(fila));
        return row;
    }

    /** "horario − marca_entrada" (llegó antes) + "marca_salida − horario" (salió después), en minutos. */
    private Integer horasExtraDiaSimple(
            String horarioEntrada, String marcaEntrada, String marcaSalida, String horarioSalida) {
        LocalTime he = parseHora(horarioEntrada);
        LocalTime me = parseHora(marcaEntrada);
        LocalTime ms = parseHora(marcaSalida);
        LocalTime hs = parseHora(horarioSalida);
        if (he == null || me == null || ms == null || hs == null) {
            return null;
        }
        long antesEntrada = java.time.Duration.between(me, he).toMinutes();
        long despuesSalida = java.time.Duration.between(hs, ms).toMinutes();
        return (int) (antesEntrada + despuesSalida);
    }

    /** "horario_salida − marca_salida" en minutos, solo si es positivo (salió antes); null si no aplica. */
    private Integer minutosSalidaAnticipada(String horarioSalida, String marcaSalida) {
        LocalTime hs = parseHora(horarioSalida);
        LocalTime ms = parseHora(marcaSalida);
        if (hs == null || ms == null) {
            return null;
        }
        long diferencia = java.time.Duration.between(ms, hs).toMinutes();
        return diferencia > 0 ? (int) diferencia : null;
    }

    private LocalTime parseHora(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : FORMATOS_HORA) {
            try {
                return LocalTime.parse(texto.trim(), f);
            } catch (DateTimeParseException ignored) {
                // siguiente formato
            }
        }
        return null;
    }

    /** Mismo criterio que la pantalla (tooltip de papeleta): tipo · motivo · horario, + Teletrabajo. */
    private String permisoPapeletaTeletrabajo(AsistenciaDiariaRowDto fila) {
        List<String> partes = new ArrayList<>();
        if (fila.isTienePapeletaAprobada()) {
            List<String> detallePapeleta = new ArrayList<>();
            if (fila.getPapeletaTipo() != null) detallePapeleta.add(fila.getPapeletaTipo());
            if (fila.getPapeletaMotivo() != null) detallePapeleta.add(fila.getPapeletaMotivo());
            String horario = papeletaHorario(fila);
            if (horario != null) detallePapeleta.add(horario);
            partes.add(String.join(" · ", detallePapeleta));
        }
        if (fila.isTieneTeletrabajo()) {
            partes.add("Teletrabajo");
        }
        return partes.isEmpty() ? null : String.join(" | ", partes);
    }

    private String papeletaHorario(AsistenciaDiariaRowDto fila) {
        String ini = fila.getPapeletaHoraInicio();
        String fin = fila.getPapeletaHoraFin();
        Double horas = fila.getPapeletaCantidadHoras();
        if (ini != null && fin != null) {
            return horas != null ? (ini + "-" + fin + " (" + horas + " h)") : (ini + "-" + fin);
        }
        return horas != null ? (horas + " h") : null;
    }

    private String direccionUnidadDe(EmpleadoPuesto puesto) {
        if (puesto == null) {
            return null;
        }
        if (puesto.getOficina() != null && puesto.getOficina().getNombre() != null) {
            return puesto.getOficina().getNombre();
        }
        return puesto.getDependencia() != null ? puesto.getDependencia().getNombre() : null;
    }

    /** De entre el historial de puesto del empleado, el vigente en {@code fecha} (o el más reciente si ninguno calza). */
    private EmpleadoPuesto oficinaVigente(List<EmpleadoPuesto> historial, LocalDate fecha) {
        if (historial == null || historial.isEmpty()) {
            return null;
        }
        return historial.stream()
                .filter(p -> p.getFechaInicio() != null && !p.getFechaInicio().isAfter(fecha)
                        && (p.getFechaFin() == null || !p.getFechaFin().isBefore(fecha)))
                .findFirst()
                .orElse(null);
    }

    private JornadaRegimen jornadaDe(Long empleadoId) {
        return empleadoPlanillaRepository.findByEmpleadoIdAndActivo(empleadoId, 1).stream()
                .findFirst()
                .map(ep -> jornadaRegimenRepository.findByRegimenLaboralId(ep.getRegimenLaboralId()).orElse(null))
                .orElse(null);
    }

    private String valor(String v) {
        return v != null ? v : "";
    }

    /** Primer valor no-vacío (blank-safe): dato específico del día, o el default del régimen. */
    private String primero(String preferido, String respaldo) {
        return (preferido != null && !preferido.isBlank()) ? preferido : respaldo;
    }
}
