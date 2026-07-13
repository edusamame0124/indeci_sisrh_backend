package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.dto.CorreccionSaldoDto;
import com.indeci.rrhh.dto.RecalculoManualResultDto;
import com.indeci.rrhh.dto.VacacionCalculoDto;
import com.indeci.rrhh.dto.VacacionCalculoInput;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.Vacacion;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.VacacionRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacacionProvisionService {

    /** SPEC_VACACIONES F9.1 — jornada por defecto del sistema (umbral récord 210 = estándar
     *  administrativo). Fallback final cuando no hay override ni config de régimen. */
    private static final int JORNADA_DEFECTO_DIAS = 5;

    /** F9.3-bis — origen de las filas insertadas por el recálculo manual (inmutabilidad). */
    private static final String ORIGEN_RECALCULO = "RECALCULO_SISTEMA";

    /** Longitud del sustento que se conserva como marcador corto en OBSERVACION (VARCHAR2(200)). */
    private static final int SUSTENTO_TRUNCADO_MAX = 150;

    /** D.Leg. 1405 — 30 días de vacaciones por cada período (año completo de servicio). */
    private static final double DIAS_POR_PERIODO = 30d;

    /** Estado de una papeleta de vacaciones REALMENTE gozada (excluye SUSTITUIDO/reprogramadas). */
    private static final String ESTADO_GOZADO = "GOZADO";

    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final VacacionCalculoService vacacionCalculoService;
    private final VacacionSaldoRepository vacacionSaldoRepository;
    private final TiempoServicioService tiempoServicioService;
    private final JornadaRegimenRepository jornadaRegimenRepository;
    private final AuditoriaContext auditoriaContext;
    private final VacacionRepository vacacionRepository;

    /** Resultado de {@link #provisionar} — distingue "recién creado" de "ya existía" (idempotencia). */
    public enum ProvisionResultado { CREADO, YA_EXISTIA }

    @Transactional
    public ProvisionResultado provisionar(Long empleadoId, int anioPeriodo) {
        List<EmpleadoPlanilla> vinculos = empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1);
        if (vinculos.isEmpty()) {
            throw new NegocioException("Empleado no tiene vinculo activo");
        }

        // Idempotencia estricta: si ya existe la provisión de este año, no se hace NADA más
        // (ni siquiera se recalcula el récord) — permite correr el job las veces que sea
        // necesario en la misma noche sin duplicar un solo día.
        boolean yaExiste = vacacionSaldoRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)
                .stream().anyMatch(s -> s.getAnio() != null && s.getAnio() == anioPeriodo);
        if (yaExiste) {
            log.info("El empleado {} ya tiene provisión para el año {}", empleadoId, anioPeriodo);
            return ProvisionResultado.YA_EXISTIA;
        }

        LocalDate fechaIngreso = fechaIngreso(vinculos);
        double diasGanadosCorrectos = calcularDiasGanadosCorrectos(empleadoId, vinculos, fechaIngreso, anioPeriodo);

        if (diasGanadosCorrectos <= 0d) {
            throw new NegocioException(
                    "El empleado no cumple con el récord vacacional para el año " + anioPeriodo);
        }

        VacacionSaldo saldo = new VacacionSaldo();
        saldo.setEmpleadoId(empleadoId);
        saldo.setAnio(anioPeriodo);
        saldo.setDiasGanados(diasGanadosCorrectos);
        saldo.setDiasGozados(0d);
        saldo.setOrigen("MOTOR_PROVISION");
        saldo.setActivo(1);
        saldo.setCreatedAt(LocalDateTime.now());
        vacacionSaldoRepository.save(saldo);
        log.info("Provisión generada para empleado {} año {}", empleadoId, anioPeriodo);
        return ProvisionResultado.CREADO;
    }

    /**
     * Recalcula TODO el saldo vacacional de un empleado — botón manual "Provisionar Auto" del
     * Padrón Vacacional. A diferencia de {@link #provisionar} / {@link #jobProvisionAutomatica}
     * (estrictamente idempotentes, pensados para el job nocturno masivo), este método SIEMPRE
     * reconstruye el estado correcto y lo reconcilia contra la BD.
     *
     * <p><b>Fuente de verdad tras recalcular (el Excel muere, la papeleta manda):</b>
     * <ul>
     *   <li><b>Ganados/Corresponden</b>: récord real por año de aniversario (D.Leg. 1405),
     *       estrictamente por tiempo de servicio (30 por período válido), SIN piso de
     *       seguridad (nunca se infla para igualar los gozados).</li>
     *   <li><b>Gozados</b>: NO se hereda el {@code DIAS_GOZADOS} del Excel (columna contaminada).
     *       Se toma el goce REAL = suma de días de las papeletas aprobadas (tabla
     *       {@code Vacacion}, estado {@code GOZADO}) y se distribuye <b>FIFO</b> sobre los
     *       períodos válidos (más antiguo primero), reutilizando el mismo criterio que
     *       {@code VacacionService.descontarSaldoVacacional} para evitar caducidad/contingencias.</li>
     * </ul>
     *
     * <p><b>Inmutabilidad — nunca in-place update:</b> las filas cuyo (ganados, gozados) no
     * coincida con el estado deseado se marcan {@code activo=0} (soft-delete, jamás DELETE
     * físico) y se INSERTA la fila limpia con {@code ORIGEN='RECALCULO_SISTEMA'}. El histórico
     * defectuoso queda consultable para auditoría; solo deja de contar en los agregados
     * (que filtran {@code activo=1}). El {@code sustento} (obligatorio, Poka-Yoke) va íntegro
     * a AUDITORIA (vía {@code @Auditable}) y como marcador corto en {@code OBSERVACION}.</p>
     */
    @Auditable(accion = "PROVISIONAR_AUTO_RECALCULO")
    @Transactional
    public RecalculoManualResultDto recalcularProvisionManual(Long empleadoId, String sustento) {
        if (sustento == null || sustento.isBlank()) {
            throw new NegocioException("El sustento de la provisión/recálculo es obligatorio");
        }

        List<EmpleadoPlanilla> vinculos = empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1);
        if (vinculos.isEmpty()) {
            throw new NegocioException("Empleado no tiene vinculo activo");
        }
        LocalDate fechaIngreso = fechaIngreso(vinculos);

        // ── ESTADO DESEADO ────────────────────────────────────────────────────────────────
        // 1) Períodos VÁLIDOS (aniversarios cumplidos con récord OK), en orden ASCENDENTE
        //    (más antiguo primero) — cada uno gana 30 días.
        List<Integer> periodosValidos = new ArrayList<>();
        long aniosCumplidos = ChronoUnit.YEARS.between(fechaIngreso, LocalDate.now());
        for (long n = 1; n <= aniosCumplidos; n++) {
            int anio = fechaIngreso.getYear() + (int) n;
            if (calcularDiasGanadosCorrectos(empleadoId, vinculos, fechaIngreso, anio) > 0d) {
                periodosValidos.add(anio);
            }
        }

        // 2) Goce REAL desde las papeletas aprobadas (NO desde DIAS_GOZADOS, contaminado por
        //    el Excel). Se excluyen las reprogramadas/sustituidas (estado != GOZADO).
        double totalGozadoReal = vacacionRepository.findByEmpleadoIdAndActivo(empleadoId, 1).stream()
                .filter(v -> ESTADO_GOZADO.equals(v.getEstado()) && v.getDias() != null)
                .mapToDouble(Vacacion::getDias)
                .sum();

        // 3) Distribución FIFO del goce real sobre los períodos válidos (más antiguo primero).
        Map<Integer, Double> gozadoPorAnio = distribuirGozadoFifo(periodosValidos, totalGozadoReal);

        // ── RECONCILIACIÓN CON LA BD (soft-delete + insert, sin churn si ya coincide) ───────
        List<VacacionSaldo> existentes = vacacionSaldoRepository.findByEmpleadoIdAndActivo(empleadoId, 1);
        List<CorreccionSaldoDto> cambios = new ArrayList<>();
        List<String> detalleAuditoria = new ArrayList<>();
        int sinCambios = 0;
        String marcador = construirMarcadorAnulacion(sustento);
        Set<Integer> periodosSatisfechos = new HashSet<>();

        for (VacacionSaldo fila : existentes) {
            Integer anio = fila.getAnio();
            Double gozadoDeseado = anio != null ? gozadoPorAnio.get(anio) : null;
            double ganadosPrevio = fila.getDiasGanados() != null ? fila.getDiasGanados() : 0d;
            double gozadosPrevio = fila.getDiasGozados() != null ? fila.getDiasGozados() : 0d;

            boolean coincide = gozadoDeseado != null
                    && !periodosSatisfechos.contains(anio)
                    && Double.compare(ganadosPrevio, DIAS_POR_PERIODO) == 0
                    && Double.compare(gozadosPrevio, gozadoDeseado) == 0;

            if (coincide) {
                periodosSatisfechos.add(anio);
                sinCambios++;
                continue;
            }

            // Fila obsoleta (Excel, período inválido o goce desactualizado): anular (soft-delete).
            // saveAndFlush (no save): el índice único parcial rige sobre filas ACTIVAS y Hibernate
            // ejecuta los INSERT antes que los UPDATE; el flush baja el soft-delete primero para
            // que nunca haya dos filas activas del mismo (empleado, año).
            fila.setActivo(0);
            fila.setObservacion(marcador);
            vacacionSaldoRepository.saveAndFlush(fila);
            cambios.add(new CorreccionSaldoDto(
                    anio != null ? anio : 0, ganadosPrevio,
                    gozadoDeseado != null ? DIAS_POR_PERIODO : 0d, gozadosPrevio, "ANULADO"));
            detalleAuditoria.add(String.format("año %s: fila #%d anulada (ganados %.1f, gozados %.1f)",
                    String.valueOf(anio), fila.getId(), ganadosPrevio, gozadosPrevio));
            log.info("Recálculo manual — empleado {} año {}: fila #{} anulada", empleadoId, anio, fila.getId());
        }

        // Insertar los períodos válidos que no quedaron satisfechos por una fila ya correcta.
        for (int anio : periodosValidos) {
            if (periodosSatisfechos.contains(anio)) {
                continue;
            }
            double gozados = gozadoPorAnio.getOrDefault(anio, 0d);
            VacacionSaldo nueva = new VacacionSaldo();
            nueva.setEmpleadoId(empleadoId);
            nueva.setAnio(anio);
            nueva.setDiasGanados(DIAS_POR_PERIODO);
            nueva.setDiasGozados(gozados);
            nueva.setOrigen(ORIGEN_RECALCULO);
            nueva.setObservacion(marcador);
            nueva.setActivo(1);
            nueva.setCreatedAt(LocalDateTime.now());
            vacacionSaldoRepository.save(nueva);
            cambios.add(new CorreccionSaldoDto(anio, 0d, DIAS_POR_PERIODO, gozados, "CREADO"));
            detalleAuditoria.add(String.format("año %d: creado (ganados %.0f, gozados %.1f)", anio, DIAS_POR_PERIODO, gozados));
            log.info("Recálculo manual — empleado {} año {}: fila creada (ganados 30, gozados {})", empleadoId, anio, gozados);
        }

        auditoriaContext.setDetalle(
                "Provisionar Auto — empleado " + empleadoId + ". Sustento: " + sustento
                        + ". Goce real (papeletas): " + totalGozadoReal
                        + (detalleAuditoria.isEmpty() ? ". Sin cambios." : ". Cambios: " + String.join(" | ", detalleAuditoria)));

        return new RecalculoManualResultDto(cambios, sinCambios);
    }

    /**
     * Reparte {@code totalGozado} FIFO sobre {@code periodosOldestFirst} (más antiguo primero),
     * cada período tope {@link #DIAS_POR_PERIODO}. El excedente (over-goce) recae en el último
     * período — mismo criterio que {@code VacacionService.descontarSaldoVacacional}.
     */
    private Map<Integer, Double> distribuirGozadoFifo(List<Integer> periodosOldestFirst, double totalGozado) {
        Map<Integer, Double> resultado = new LinkedHashMap<>();
        double restante = Math.max(0d, totalGozado);
        for (int anio : periodosOldestFirst) {
            double asignado = Math.min(DIAS_POR_PERIODO, restante);
            resultado.put(anio, asignado);
            restante -= asignado;
        }
        if (restante > 0d && !periodosOldestFirst.isEmpty()) {
            int ultimo = periodosOldestFirst.get(periodosOldestFirst.size() - 1);
            resultado.merge(ultimo, restante, Double::sum);
        }
        return resultado;
    }

    /** Marcador corto para OBSERVACION (VARCHAR2(200)) — el sustento íntegro va en AUDITORIA. */
    private String construirMarcadorAnulacion(String sustento) {
        String truncado = sustento.length() > SUSTENTO_TRUNCADO_MAX
                ? sustento.substring(0, SUSTENTO_TRUNCADO_MAX) + "..."
                : sustento;
        return "ANULADO_POR_RECALCULO — Sustento: " + truncado;
    }

    /**
     * Días GANADOS que corresponden a un año de aniversario según el récord real
     * (D.Leg. 1405 art. 2.2): 30d si el período es válido y supera el umbral de días
     * efectivos (30/360 del año evaluado, netos de LSG/faltas); 0d en caso contrario.
     */
    private double calcularDiasGanadosCorrectos(
            Long empleadoId, List<EmpleadoPlanilla> vinculos, LocalDate fechaIngreso, int anioPeriodo) {

        // Récord por AÑO DE SERVICIOS: la ventana va de aniversario a aniversario del
        // INGRESO, no por año calendario.
        LocalDate corteAniversario = fechaIngreso.plusYears((long) anioPeriodo - fechaIngreso.getYear());

        // Un período solo es válido si su aniversario cae al menos 1 año completo después
        // del ingreso; de lo contrario la ventana desde/hasta caería ANTES de que el
        // empleado siquiera hubiese ingresado (bug detectado: el botón manual asumía año
        // calendario actual como período, generando ventanas de evaluación previas a la
        // fecha real de ingreso y "aprobando" récord para empleados sin 1 año de servicio).
        if (corteAniversario.isBefore(fechaIngreso.plusYears(1))) {
            return 0d;
        }

        LocalDate desde = corteAniversario.minusYears(1);
        LocalDate hasta = corteAniversario.minusDays(1);

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
        // ser el del AÑO evaluado (desde/hasta), no la carrera completa acumulada.
        int diasIdealAnio = Dias360.entre(desde, hasta) + 1;

        VacacionCalculoInput input = new VacacionCalculoInput(
                empleadoId, desde, hasta,
                ts.anios(), ts.meses(), ts.dias(), diasIdealAnio,
                0, 0, BigDecimal.ZERO, jornadaDiasSemana, 0
        );

        // VacacionCalculoService ya descuenta LSG/faltas reales internamente (vía
        // IncidenciaLaboralProvider) cuando empleadoId/fechaDesde/fechaHasta vienen no-nulos.
        VacacionCalculoDto calc = vacacionCalculoService.calcular(input);
        return VacacionCalculoDto.RECORD_OK.equals(calc.estadoRecord()) ? 30d : 0d;
    }

    private LocalDate fechaIngreso(List<EmpleadoPlanilla> vinculos) {
        return vinculos.stream()
                .map(EmpleadoPlanilla::getFechaInicioContrato)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElseThrow(() -> new NegocioException(
                        "El vínculo del empleado no tiene fecha de inicio de contrato"));
    }

    /**
     * Escaneo nocturno: para cada empleado con vínculo activo, provisiona TODOS los años de
     * servicio ya cumplidos (aniversarios pasados) que aún no tengan saldo generado.
     *
     * <p><b>Idempotencia estricta:</b> delega en {@link #provisionar}, que ya verifica
     * {@code yaExiste} antes de escribir — correr este job 10 veces la misma noche no duplica
     * un solo día (cada año ya provisionado se detecta y se salta).</p>
     *
     * <p><b>Tolerancia a fallos:</b> cada intento (empleado × año) va en su propio try-catch;
     * un bloqueo por récord o un error técnico en un servidor NUNCA interrumpe el resto de la
     * nómina.</p>
     */
    @Scheduled(cron = "0 0 2 * * ?") // Todos los días a las 2 AM
    public void jobProvisionAutomatica() {
        log.info("Iniciando job de provisión automática...");
        final LocalDate hoy = LocalDate.now();

        List<Long> empleadoIds = empleadoPlanillaRepository.findByActivo(1).stream()
                .map(EmpleadoPlanilla::getEmpleadoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int provisionados = 0;
        int yaExistian = 0;
        int bloqueadosPorRecord = 0;
        int erroresTecnicos = 0;

        for (Long empleadoId : empleadoIds) {
            try {
                List<EmpleadoPlanilla> vinculos =
                        empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1);
                LocalDate fechaIngreso = vinculos.stream()
                        .map(EmpleadoPlanilla::getFechaInicioContrato)
                        .filter(Objects::nonNull)
                        .min(LocalDate::compareTo)
                        .orElse(null);
                if (fechaIngreso == null || fechaIngreso.isAfter(hoy)) {
                    continue; // sin fecha de ingreso válida, o ingreso aún futuro: nada que evaluar.
                }

                // Todos los aniversarios (años de servicio) ya cumplidos a la fecha de hoy.
                long aniosCumplidos = ChronoUnit.YEARS.between(fechaIngreso, hoy);
                for (long n = 1; n <= aniosCumplidos; n++) {
                    int anioPeriodo = fechaIngreso.getYear() + (int) n;
                    try {
                        ProvisionResultado resultado = provisionar(empleadoId, anioPeriodo);
                        if (resultado == ProvisionResultado.CREADO) {
                            provisionados++;
                        } else {
                            yaExistian++;
                        }
                    } catch (NegocioException e) {
                        if (e.getMessage() != null && e.getMessage().contains("récord vacacional")) {
                            bloqueadosPorRecord++;
                        } else {
                            erroresTecnicos++;
                            log.warn("Provisión automática — empleado {} año {}: {}",
                                    empleadoId, anioPeriodo, e.getMessage());
                        }
                    } catch (Exception e) {
                        erroresTecnicos++;
                        log.error("Provisión automática — error técnico empleado {} año {}",
                                empleadoId, anioPeriodo, e);
                    }
                }
            } catch (Exception e) {
                erroresTecnicos++;
                log.error("Provisión automática — error técnico procesando empleado {}", empleadoId, e);
            }
        }

        log.info("Provisión Automática Finalizada: {} provisionados con éxito, {} ya existían, "
                        + "{} bloqueados por SIN_RECORD_LEGAL, {} errores técnicos",
                provisionados, yaExistian, bloqueadosPorRecord, erroresTecnicos);
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
