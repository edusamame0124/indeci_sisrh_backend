package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.exception.VinculoNoEncontradoException;
import com.indeci.rrhh.dto.TiempoServicioDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.service.support.Dias360;

import lombok.RequiredArgsConstructor;

/**
 * Cómputo del <b>tiempo de servicio</b> de un empleado a partir de sus vínculos
 * (INDECI_EMPLEADO_PLANILLA) — SPEC_VACACIONES F1. Base reutilizable por Vacaciones,
 * CTS y LBS. Read-only; no muta estado.
 *
 * <p>Reglas (decisiones cerradas):
 * <ul>
 *   <li><b>Ancla de inicio (D-F1-1):</b> {@code fechaInicioContrato}; si es nula,
 *       {@code fechaIngreso} solo si está poblada (legacy). <b>Nunca</b>
 *       {@code fechaInicio} (es {@code now()} de creación del registro).</li>
 *   <li><b>Base 30/360 US/NASD (D-F1-2)</b> vía {@link Dias360}, con "+1" inclusivo
 *       por intervalo (reproduce columnas L/M/N del Excel del especialista).</li>
 *   <li><b>Fusión de intervalos solapados + exclusión de huecos (D-F1-3):</b> se
 *       fusionan vínculos traslapados (evita doble conteo en rotación CAS) y los
 *       huecos entre contratos NO cuentan como servicio.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TiempoServicioService {

    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;

    /**
     * Calcula el tiempo de servicio del empleado hasta {@code fechaCorte}.
     *
     * @param empleadoId empleado
     * @param fechaCorte fecha de corte; si es {@code null} se usa HOY
     * @throws VinculoNoEncontradoException si no hay vínculo activo con fechas válidas
     */
    public TiempoServicioDto calcular(Long empleadoId, LocalDate fechaCorte) {
        final List<EmpleadoPlanilla> vinculos =
                empleadoPlanillaRepository.findByEmpleadoIdAndActivo(empleadoId, 1);
        return calcularDesde(vinculos, empleadoId, fechaCorte)
                .orElseThrow(() -> new VinculoNoEncontradoException(
                        "El empleado " + empleadoId
                                + " no tiene vínculo activo (o sin fechas válidas) para calcular tiempo de servicio"));
    }

    /**
     * Variante <b>batch-friendly</b> (SPEC_VACACIONES F4): calcula el tiempo de servicio a partir
     * de vínculos ya cargados, sin acceder al repositorio (evita N+1 en el padrón). Devuelve
     * {@code Optional.empty()} si no hay vínculos con fechas válidas (el padrón lo muestra como
     * "sin vínculo" en vez de lanzar 404).
     */
    public Optional<TiempoServicioDto> calcularDesde(
            List<EmpleadoPlanilla> vinculos, Long empleadoId, LocalDate fechaCorte) {
        final LocalDate corte = fechaCorte != null ? fechaCorte : LocalDate.now();

        if (vinculos == null || vinculos.isEmpty()) {
            return Optional.empty();
        }

        // 1) Construir intervalos [inicio, fin] válidos, recortados al corte.
        final List<LocalDate[]> intervalos = new ArrayList<>();
        for (EmpleadoPlanilla v : vinculos) {
            final LocalDate ini = anclaInicio(v);
            if (ini == null) {
                continue; // vínculo sin ancla de inicio → se descarta
            }
            LocalDate fin = finVinculo(v, corte);
            if (fin.isAfter(corte)) {
                fin = corte;
            }
            if (ini.isAfter(fin)) {
                continue; // inicio posterior al corte → no aporta servicio aún
            }
            intervalos.add(new LocalDate[] { ini, fin });
        }
        if (intervalos.isEmpty()) {
            return Optional.empty();
        }

        // 2) Fusionar intervalos solapados o adyacentes (excluye huecos reales).
        intervalos.sort(Comparator.comparing(a -> a[0]));
        final List<LocalDate[]> fusionados = new ArrayList<>();
        boolean tieneTraslape = false;
        LocalDate[] actual = new LocalDate[] { intervalos.get(0)[0], intervalos.get(0)[1] };
        for (int i = 1; i < intervalos.size(); i++) {
            final LocalDate[] siguiente = intervalos.get(i);
            if (!siguiente[0].isAfter(actual[1])) {
                // Traslape real (comienza en/antes del fin actual).
                tieneTraslape = true;
                if (siguiente[1].isAfter(actual[1])) {
                    actual[1] = siguiente[1];
                }
            } else if (!siguiente[0].isAfter(actual[1].plusDays(1))) {
                // Adyacente (contiguo o 1 día de separación) → continuidad, sin doble conteo.
                if (siguiente[1].isAfter(actual[1])) {
                    actual[1] = siguiente[1];
                }
            } else {
                // Hueco real → cerrar intervalo y abrir uno nuevo.
                fusionados.add(actual);
                actual = new LocalDate[] { siguiente[0], siguiente[1] };
            }
        }
        fusionados.add(actual);

        // 3) Sumar días 30/360 + 1 inclusivo por intervalo fusionado.
        int total = 0;
        for (LocalDate[] iv : fusionados) {
            total += Dias360.entre(iv[0], iv[1]) + 1;
        }

        // 4) Desglosar en años/meses/días (base 30/360).
        final int anios = total / 360;
        final int meses = (total - anios * 360) / 30;
        final int dias = total - anios * 360 - meses * 30;

        final LocalDate fechaIngreso = fusionados.get(0)[0];

        return Optional.of(new TiempoServicioDto(
                empleadoId, fechaIngreso, corte,
                anios, meses, dias, total,
                vinculos.size(), tieneTraslape));
    }

    /** Ancla de inicio: fechaInicioContrato; si nula, fechaIngreso (legacy). Nunca fechaInicio. */
    private LocalDate anclaInicio(EmpleadoPlanilla v) {
        if (v.getFechaInicioContrato() != null) {
            return v.getFechaInicioContrato();
        }
        return v.getFechaIngreso();
    }

    /** Fin del vínculo: fechaFin; si nula, fechaCese; si nula, el corte (vínculo vigente). */
    private LocalDate finVinculo(EmpleadoPlanilla v, LocalDate corte) {
        if (v.getFechaFin() != null) {
            return v.getFechaFin();
        }
        if (v.getFechaCese() != null) {
            return v.getFechaCese();
        }
        return corte;
    }
}
