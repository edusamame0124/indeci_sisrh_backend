package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaResumenPeriodoRowDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.service.PersonaService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reporte de asistencia consolidado por período (1 fila por empleado x período),
 * formato institucional {@code docs/reporte_asistencia.xlsx}. Arma cada fila desde
 * {@code AsistenciaCabecera} (modelo de tardanza de dos niveles, V010_95) — no
 * recalcula nada, solo lee y proyecta lo que el motor de asistencia ya persistió.
 *
 * <p>"Permisos particulares" = papeletas APROBADAS de código "001" (Permiso por
 * asuntos personales, seed V012_36 = sin goce) que se solapan con el período;
 * es una sumatoria de reporte, independiente de la clasificación diaria
 * (tipoDia/FALTA) que resuelve {@code PapeletaJustificacionResolver}.
 */
@Service
@RequiredArgsConstructor
public class AsistenciaResumenPeriodoService {

    private static final long ESTADO_SOLICITUD_APROBADA = 9L;
    private static final List<String> CODIGOS_PERMISO_PARTICULAR = List.of("001");
    private static final BigDecimal JORNADA_HORAS_DEFAULT = BigDecimal.valueOf(8);

    private final AsistenciaCabeceraRepository cabeceraRepository;
    private final PersonaService personaService;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final JornadaRegimenRepository jornadaRegimenRepository;
    private final EmpleadoPuestoRepository empleadoPuestoRepository;
    private final SolicitudRrhhRepository solicitudRrhhRepository;

    @Transactional(readOnly = true)
    public List<AsistenciaResumenPeriodoRowDto> generar(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaFin.isBefore(fechaInicio)) {
            throw new NegocioException("La fecha fin debe ser posterior o igual a la de inicio.");
        }

        Map<Long, PersonaResumenDto> personasPorEmpleado = personaService.listar().stream()
                .collect(Collectors.toMap(PersonaResumenDto::getEmpleadoId, Function.identity(), (a, b) -> a));

        List<AsistenciaResumenPeriodoRowDto> filas = new ArrayList<>();
        for (String periodo : periodosEnRango(fechaInicio, fechaFin)) {
            filas.addAll(filasDelPeriodo(periodo, fechaInicio, fechaFin, personasPorEmpleado));
        }
        return filas;
    }

    private List<AsistenciaResumenPeriodoRowDto> filasDelPeriodo(
            String periodo, LocalDate fechaInicio, LocalDate fechaFin,
            Map<Long, PersonaResumenDto> personasPorEmpleado) {

        List<AsistenciaCabecera> cabeceras = cabeceraRepository.findByPeriodoAndActivo(periodo, 1);
        if (cabeceras.isEmpty()) {
            return List.of();
        }

        YearMonth mes = YearMonth.parse(periodo);
        LocalDate finMes = mes.atEndOfMonth();
        LocalDate desdeEfectivo = mes.atDay(1).isAfter(fechaInicio) ? mes.atDay(1) : fechaInicio;
        LocalDate hastaEfectivo = finMes.isBefore(fechaFin) ? finMes : fechaFin;

        List<Long> empleadoIds = cabeceras.stream().map(AsistenciaCabecera::getEmpleadoId).toList();

        Map<Long, EmpleadoPuesto> puestoPorEmpleado = empleadoPuestoRepository
                .findVigentesEnFecha(empleadoIds, finMes).stream()
                .collect(Collectors.toMap(EmpleadoPuesto::getEmpleadoId, Function.identity(), (a, b) -> a));

        Map<Long, Double> horasPermisoPorEmpleado = solicitudRrhhRepository
                .sumarHorasPorTipoEnRango(ESTADO_SOLICITUD_APROBADA, CODIGOS_PERMISO_PARTICULAR, desdeEfectivo, hastaEfectivo)
                .stream()
                .collect(Collectors.toMap(
                        SolicitudRrhhRepository.HorasPermisoAgg::getEmpleadoId,
                        SolicitudRrhhRepository.HorasPermisoAgg::getTotalHoras));

        List<AsistenciaResumenPeriodoRowDto> filas = new ArrayList<>();
        for (AsistenciaCabecera cab : cabeceras) {
            filas.add(construirFila(
                    cab,
                    personasPorEmpleado.get(cab.getEmpleadoId()),
                    puestoPorEmpleado.get(cab.getEmpleadoId()),
                    horasPermisoPorEmpleado.getOrDefault(cab.getEmpleadoId(), 0.0)));
        }
        return filas;
    }

    private AsistenciaResumenPeriodoRowDto construirFila(
            AsistenciaCabecera cab, PersonaResumenDto persona, EmpleadoPuesto puesto, double horasPermiso) {

        double remun = cab.getRemuneracionBase() != null ? cab.getRemuneracionBase() : 0.0;
        BigDecimal jornadaHoras = jornadaHorasDe(cab.getEmpleadoId());

        AsistenciaResumenPeriodoRowDto row = new AsistenciaResumenPeriodoRowDto();
        row.setPeriodo(cab.getPeriodo());
        row.setDni(persona != null ? persona.getDni() : null);
        row.setApellidosNombres(persona != null ? persona.getNombreCompleto() : null);
        row.setRegimen(persona != null ? persona.getRegimenLaboral() : null);
        row.setDireccionUnidad(direccionUnidadDe(puesto));

        row.setRemuneracionMensual(remun);
        row.setCostoDia(costoDia(remun));
        row.setCostoHora(costoHora(remun, jornadaHoras));
        row.setCostoMinuto(costoMinuto(remun, jornadaHoras));

        row.setMinutosDescuento1(valor(cab.getMinTardanzaDiaria()));
        row.setDescuento1Diaria(valor(cab.getDescuentoTardanzaDiaria()));
        row.setMinutosMenorAcumTotal(valor(cab.getMinTardanzaMenorAcum()));
        row.setMinutosExcedentesTope(valor(cab.getMinTardanzaExcesoMes()));
        row.setDescuento2Mensual(valor(cab.getDescuentoTardanzaMensual()));
        row.setTotalMinutosDescuento1y2(valor(cab.getTotalMinTardanza()));
        row.setTotalDescuento1y2(valor(cab.getDescuentoTardanza()));

        int minutosPermiso = (int) Math.round(horasPermiso * 60);
        row.setMinutosPermisosParticulares(minutosPermiso);
        row.setDescuentoPermisosParticulares(
                TardanzaDescuentoCalculator.descuento(remun, jornadaHoras, minutosPermiso));

        row.setTotalFaltas(valor(cab.getDiasFalta()));
        row.setDescuentoFaltas(valor(cab.getDescuentoFalta()));
        return row;
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

    /** Jornada vigente del empleado vía su régimen (misma resolución que el motor de asistencia). */
    private BigDecimal jornadaHorasDe(Long empleadoId) {
        JornadaRegimen jornada = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(ep -> jornadaRegimenRepository.findByRegimenLaboralId(ep.getRegimenLaboralId()).orElse(null))
                .orElse(null);
        return (jornada != null && jornada.getJornadaHoras() != null)
                ? jornada.getJornadaHoras() : JORNADA_HORAS_DEFAULT;
    }

    private double costoDia(double remuneracion) {
        return remuneracion > 0 ? redondear(remuneracion / 30.0) : 0.0;
    }

    private double costoHora(double remuneracion, BigDecimal jornadaHoras) {
        double horas = jornadaHoras.doubleValue();
        return (remuneracion > 0 && horas > 0) ? redondear(remuneracion / (30.0 * horas)) : 0.0;
    }

    private double costoMinuto(double remuneracion, BigDecimal jornadaHoras) {
        double horas = jornadaHoras.doubleValue();
        return (remuneracion > 0 && horas > 0) ? redondear(remuneracion / (30.0 * horas * 60.0)) : 0.0;
    }

    private double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private int valor(Integer v) {
        return v != null ? v : 0;
    }

    private double valor(Double v) {
        return v != null ? v : 0.0;
    }

    /** Períodos "yyyy-MM" que toca [fechaInicio, fechaFin], en orden cronológico. */
    private List<String> periodosEnRango(LocalDate fechaInicio, LocalDate fechaFin) {
        List<String> periodos = new ArrayList<>();
        YearMonth actual = YearMonth.from(fechaInicio);
        YearMonth ultimo = YearMonth.from(fechaFin);
        while (!actual.isAfter(ultimo)) {
            periodos.add(actual.toString());
            actual = actual.plusMonths(1);
        }
        return periodos;
    }
}
