package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.VacacionCalculoDto;
import com.indeci.rrhh.dto.VacacionCalculoInput;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;
import com.indeci.rrhh.service.support.Dias360;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacacionProvisionService {

    /** SPEC_VACACIONES F9.1 — jornada por defecto del sistema (umbral récord 210 = estándar
     *  administrativo). Fallback final cuando no hay override ni config de régimen. */
    private static final int JORNADA_DEFECTO_DIAS = 5;

    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final VacacionCalculoService vacacionCalculoService;
    private final VacacionSaldoRepository vacacionSaldoRepository;
    private final TiempoServicioService tiempoServicioService;
    private final JornadaRegimenRepository jornadaRegimenRepository;

    @Transactional
    public void provisionar(Long empleadoId, int anioPeriodo) {
        List<EmpleadoPlanilla> vinculos = empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1);
        if (vinculos.isEmpty()) {
            throw new NegocioException("Empleado no tiene vinculo activo");
        }

        // Verificar si ya tiene provisión
        boolean yaExiste = vacacionSaldoRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)
                .stream().anyMatch(s -> s.getAnio() != null && s.getAnio() == anioPeriodo);
        if (yaExiste) {
            log.info("El empleado {} ya tiene provisión para el año {}", empleadoId, anioPeriodo);
            return;
        }

        // Récord por AÑO DE SERVICIOS (D.Leg. 1405 art. 2.2): la ventana va de aniversario
        // a aniversario del INGRESO, no por año calendario. Tomamos la fecha de ingreso
        // (inicio del servicio continuo = el vínculo más antiguo) y evaluamos el año de
        // servicios cuyo aniversario cae en anioPeriodo.
        LocalDate fechaIngreso = vinculos.stream()
                .map(EmpleadoPlanilla::getFechaInicioContrato)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElseThrow(() -> new NegocioException(
                        "El vínculo del empleado no tiene fecha de inicio de contrato"));

        LocalDate corteAniversario = fechaIngreso.plusYears((long) anioPeriodo - fechaIngreso.getYear());
        LocalDate desde = corteAniversario.minusYears(1);
        LocalDate hasta = corteAniversario.minusDays(1);

        // Calculamos tiempo de servicio a la fecha de aniversario.
        var tsOpt = tiempoServicioService.calcularDesde(vinculos, empleadoId, corteAniversario);
        if (tsOpt.isEmpty()) {
            throw new NegocioException("No se pudo calcular tiempo de servicio");
        }
        var ts = tsOpt.get();

        // SPEC_VACACIONES F9.1 — jornada del vínculo vigente (override → régimen → default 5),
        // mismo patrón de fallback que el Padrón. Antes hardcodeada a 6 (umbral 260): bloqueaba
        // indebidamente al personal administrativo (jornada real 5 → umbral correcto 210).
        final int jornadaDiasSemana = resolverJornada(vinculoVigente(vinculos));

        // Refactor Récord Anual Estricto — el total contra el que se compara el récord DEBE
        // ser el del AÑO evaluado (desde/hasta), no la carrera completa acumulada
        // (ts.totalDias360() mezclaba un descuento de un año con un total de varios años,
        // por lo que el bloqueo casi nunca se activaba para personal con antigüedad).
        int diasIdealAnio = Dias360.entre(desde, hasta) + 1;

        VacacionCalculoInput input = new VacacionCalculoInput(
                empleadoId, desde, hasta,
                ts.anios(), ts.meses(), ts.dias(), diasIdealAnio,
                0, 0, BigDecimal.ZERO, jornadaDiasSemana, 0
        );

        VacacionCalculoDto calc = vacacionCalculoService.calcular(input);

        if (VacacionCalculoDto.RECORD_OK.equals(calc.estadoRecord())) {
            VacacionSaldo saldo = new VacacionSaldo();
            saldo.setEmpleadoId(empleadoId);
            saldo.setAnio(anioPeriodo);
            saldo.setDiasGanados(30d);
            saldo.setDiasGozados(0d);
            saldo.setOrigen("MOTOR_PROVISION");
            saldo.setActivo(1);
            saldo.setCreatedAt(LocalDateTime.now());
            vacacionSaldoRepository.save(saldo);
            log.info("Provisión generada para empleado {} año {}", empleadoId, anioPeriodo);
        } else {
            throw new NegocioException("El empleado no cumple con el récord vacacional (efectivos: "
                    + calc.diasEfectivos() + " / umbral " + calc.umbralRecord() + ")");
        }
    }

    @Scheduled(cron = "0 0 2 * * ?") // Todos los días a las 2 AM
    public void jobProvisionAutomatica() {
        log.info("Iniciando job de provisión automática...");
        // Implementación del escaneo general aquí.
    }

    /** Vínculo vigente (sin cese); a falta de él, el de inicio más reciente. */
    private EmpleadoPlanilla vinculoVigente(List<EmpleadoPlanilla> vinculos) {
        return vinculos.stream()
                .max(Comparator
                        .comparingInt((EmpleadoPlanilla v) -> v.getFechaCese() == null ? 1 : 0)
                        .thenComparing(v -> Optional.ofNullable(v.getFechaInicioContrato())
                                .orElse(LocalDate.MIN)))
                .orElse(null);
    }

    /**
     * SPEC_VACACIONES F9.1 — jornada días/semana con patrón herencia/override:
     * {@code empleado.diasSemanaOperativo} ?? {@code jornadaRegimen.diasSemana} ?? 5 (default).
     */
    private int resolverJornada(EmpleadoPlanilla vigente) {
        if (vigente == null) {
            return JORNADA_DEFECTO_DIAS;
        }
        if (vigente.getDiasSemanaOperativo() != null) {
            return vigente.getDiasSemanaOperativo();
        }
        if (vigente.getRegimenLaboralId() == null) {
            return JORNADA_DEFECTO_DIAS;
        }
        return jornadaRegimenRepository.findByRegimenLaboralId(vigente.getRegimenLaboralId())
                .map(com.indeci.rrhh.entity.JornadaRegimen::getDiasSemana)
                .filter(Objects::nonNull)
                .orElse(JORNADA_DEFECTO_DIAS);
    }
}
