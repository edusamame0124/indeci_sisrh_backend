package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.VinculoNoEncontradoException;
import com.indeci.rrhh.dto.DiasNoComputablesDto;
import com.indeci.rrhh.dto.PeriodoRecordDto;
import com.indeci.rrhh.dto.RecordVacacionalDetalleDto;
import com.indeci.rrhh.dto.TiempoServicioDetalleDto;
import com.indeci.rrhh.dto.PadronVacacionalPageDto;
import com.indeci.rrhh.dto.PadronVacacionalRowDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.dto.PersonaResumenPageDto;
import com.indeci.rrhh.dto.TiempoServicioDto;
import com.indeci.rrhh.dto.VacacionCalculoDto;
import com.indeci.rrhh.dto.VacacionCalculoInput;
import com.indeci.rrhh.entity.Cargo;
import com.indeci.rrhh.entity.Dependencia;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.Vacacion;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.dto.GoceRegistradoDto;
import com.indeci.rrhh.repository.CargoRepository;
import com.indeci.rrhh.repository.DependenciaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.VacacionRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;
import com.indeci.rrhh.service.support.Dias360;

import lombok.RequiredArgsConstructor;

/**
 * Padrón vacacional pageable — SPEC_VACACIONES F4. Arma, para cada empleado, las columnas
 * del Excel del especialista (DNI, nombre, régimen, cargo, dependencia, días que corresponden,
 * gozados, saldo) + tiempo de servicio y récord provisional.
 *
 * <p>Diseño: la <b>paginación y el buscador (DNI/nombre) son server-side</b> reutilizando
 * {@code PersonaService.listarPaginado} (SQL con offset/size). Cada página se <b>enriquece en
 * batch</b> (vínculos, puestos, saldos, catálogos en pocas queries) — sin N+1. El cómputo por
 * fila delega en F1 ({@link TiempoServicioService#calcularDesde}) y F3 ({@link VacacionCalculoService}).
 *
 * <p>D5 (récord) y filtros por régimen/dependencia se afinan en F9/F4.1; jornada 6d por defecto.
 */
@Service
@RequiredArgsConstructor
public class PadronVacacionalService {

    /**
     * SPEC_VACACIONES F9.1 — jornada por defecto del sistema (umbral récord 210 = estándar
     * administrativo del Estado). Fallback final cuando no hay override ni config de régimen.
     */
    private static final int JORNADA_DEFECTO_DIAS = 5;

    /**
     * Refactor Récord Anual Estricto — el empleado aún no completa su primer año de servicio
     * (fechaIngreso + 1 año &gt; corte). El récord OK/SIN_RECORD_LEGAL no aplica todavía; se
     * muestra este estado transicional, evaluando desde el ingreso hasta la fecha de corte.
     */
    private static final String ESTADO_EN_ACUMULACION = "EN_ACUMULACION";

    private final PersonaService personaService;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final EmpleadoPuestoRepository empleadoPuestoRepository;
    private final VacacionSaldoRepository vacacionSaldoRepository;
    private final VacacionRepository vacacionRepository;
    private final CargoRepository cargoRepository;
    private final DependenciaRepository dependenciaRepository;
    private final TiempoServicioService tiempoServicioService;
    private final VacacionCalculoService vacacionCalculoService;
    private final com.indeci.rrhh.repository.JornadaRegimenRepository jornadaRegimenRepository;
    private final com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta incidenciaLaboralCompuesta;

    @Transactional(readOnly = true)
    public PadronVacacionalPageDto consultar(String q, int page, int size) {
        final PersonaResumenPageDto base = personaService.listarPaginado(q, page, size);
        final List<PersonaResumenDto> filas = base.getContent();

        final List<Long> empIds = filas.stream()
                .map(PersonaResumenDto::getEmpleadoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // ---- Enriquecimiento en batch (pocas queries por página) ----
        final Map<Long, List<EmpleadoPlanilla>> vinculosPorEmp = empIds.isEmpty()
                ? Map.of()
                : empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(empIds, 1).stream()
                        .collect(Collectors.groupingBy(EmpleadoPlanilla::getEmpleadoId));

        final Map<Long, EmpleadoPuesto> puestoPorEmp = empIds.isEmpty()
                ? Map.of()
                : empleadoPuestoRepository.findByEmpleadoIdInAndActivo(empIds, 1).stream()
                        .collect(Collectors.toMap(EmpleadoPuesto::getEmpleadoId, p -> p, (a, b) -> a));

        final Map<Long, List<VacacionSaldo>> saldosPorEmp = empIds.isEmpty()
                ? Map.of()
                : vacacionSaldoRepository.findByEmpleadoIdInAndActivo(empIds, 1).stream()
                        .collect(Collectors.groupingBy(VacacionSaldo::getEmpleadoId));

        final Map<Long, Double> ganadosPorEmp = saldosPorEmp.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .mapToDouble(s -> s.getDiasGanados() != null ? s.getDiasGanados() : 0d).sum()));

        final Map<Long, Double> gozadosPorEmp = saldosPorEmp.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .mapToDouble(s -> s.getDiasGozados() != null ? s.getDiasGozados() : 0d).sum()));

        // F9.3 — D.S. 013-2019-PCM: períodos (años) con saldo pendiente de gozar, reusando
        // el mismo batch de VacacionSaldo (sin query adicional).
        final Map<Long, Integer> periodosPendientesPorEmp = saldosPorEmp.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> VacacionService.contarPeriodosPendientes(e.getValue())));

        // V012_55 — señal de Override para "Ver detalle"/"Historial" (una query por página).
        final Set<Long> empsConOverride = empIds.isEmpty()
                ? Set.of()
                : vacacionRepository
                        .findByEmpleadoIdInAndActivoAndOrigen(empIds, 1, VacacionService.ORIGEN_OVERRIDE_RRHH)
                        .stream().map(Vacacion::getEmpleadoId).collect(Collectors.toSet());

        final Map<Long, String> cargoNombre = cargoRepository.findAll().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Cargo::getId, Cargo::getNombre, (a, b) -> a));
        final Map<Long, String> dependenciaNombre = dependenciaRepository.findAll().stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(Dependencia::getId, Dependencia::getNombre, (a, b) -> a));

        // SPEC_VACACIONES F9.1 — jornada por régimen (base del fallback). Pocos registros.
        final Map<Long, Integer> jornadaPorRegimen = jornadaRegimenRepository.findAll().stream()
                .filter(j -> j.getRegimenLaboralId() != null && j.getDiasSemana() != null)
                .collect(Collectors.toMap(
                        com.indeci.rrhh.entity.JornadaRegimen::getRegimenLaboralId,
                        com.indeci.rrhh.entity.JornadaRegimen::getDiasSemana, (a, b) -> a));

        final LocalDate corte = LocalDate.now();
        final List<PadronVacacionalRowDto> content = new ArrayList<>(filas.size());

        for (PersonaResumenDto f : filas) {
            final Long empId = f.getEmpleadoId();
            final List<EmpleadoPlanilla> vinculos = empId != null
                    ? vinculosPorEmp.getOrDefault(empId, List.of())
                    : List.of();
            final EmpleadoPuesto puesto = empId != null ? puestoPorEmp.get(empId) : null;
            final String cargo = puesto != null ? cargoNombre.get(puesto.getCargoId()) : null;
            final String dependencia = puesto != null ? dependenciaNombre.get(puesto.getDependenciaId()) : null;
            final double ganados = empId != null ? ganadosPorEmp.getOrDefault(empId, 0d) : 0d;
            final double gozados = empId != null ? gozadosPorEmp.getOrDefault(empId, 0d) : 0d;
            final int periodosPendientes = empId != null ? periodosPendientesPorEmp.getOrDefault(empId, 0) : 0;
            final boolean requiereDecision = periodosPendientes > VacacionService.TOPE_PERIODOS_ACUMULACION;

            final Optional<TiempoServicioDto> tsOpt =
                    tiempoServicioService.calcularDesde(vinculos, empId, corte);

            final boolean tieneOverride = empId != null && empsConOverride.contains(empId);

            if (tsOpt.isEmpty()) {
                content.add(new PadronVacacionalRowDto(
                        empId, f.getDni(), f.getNombreCompleto(), f.getRegimenLaboral(),
                        cargo, dependencia,
                        null, null, null,
                        null, null, null, null, null,
                        0, gozados, -gozados,
                        "SIN_VINCULO", true,
                        periodosPendientes, requiereDecision, tieneOverride));
                continue;
            }

            final TiempoServicioDto ts = tsOpt.get();
            final EmpleadoPlanilla vigente = vinculoVigente(vinculos);
            final BigDecimal rem = vigente != null && vigente.getSueldoBasico() != null
                    ? BigDecimal.valueOf(vigente.getSueldoBasico())
                    : BigDecimal.ZERO;
            final int jornadaDias = resolverJornada(vigente, jornadaPorRegimen);

            // Refactor Récord Anual Estricto — el récord se evalúa sobre el AÑO DE SERVICIO
            // específico (el último ya completado), NUNCA sobre la antigüedad acumulada de
            // toda la carrera (bug: mezclaba un descuento de un año con un total de varios
            // años, por lo que el bloqueo casi nunca se activaba para personal con antigüedad).
            final boolean enAcumulacion = ts.anios() == 0;
            final LocalDate desdeAnio;
            final LocalDate hastaAnio;
            final int diasIdealAnio;
            if (enAcumulacion) {
                // Primer año de servicio aún no completado: se acota desde el ingreso hasta hoy.
                desdeAnio = ts.fechaIngreso();
                hastaAnio = corte;
                diasIdealAnio = ts.totalDias360();
            } else {
                // Último año de servicio YA completado (el que generó/debió generar el bloque
                // de 30 días), mismo patrón de aniversario que VacacionProvisionService.
                final LocalDate finAnio = ts.fechaIngreso().plusYears(ts.anios());
                desdeAnio = finAnio.minusYears(1);
                hastaAnio = finAnio.minusDays(1);
                diasIdealAnio = Dias360.entre(desdeAnio, hastaAnio) + 1;
            }

            // SPEC_VACACIONES F9.1 — días NO computables (LSG + faltas) acotados al MISMO año
            // evaluado (no a toda la carrera).
            final DiasNoComputablesDto noComp = incidenciaLaboralCompuesta
                    .calcularDesglose(empId, desdeAnio, hastaAnio);

            final VacacionCalculoDto calc = vacacionCalculoService.calcular(new VacacionCalculoInput(
                    null, null, null,
                    ts.anios(), ts.meses(), ts.dias(), diasIdealAnio,
                    ganados, gozados, rem, jornadaDias, noComp.total()));

            // Descomponer los días EFECTIVOS del año evaluado (30/360) para la columna "Efectivos".
            final int totalEfectivos = Math.max(0, diasIdealAnio - noComp.total());
            final int aniosEf = totalEfectivos / 360;
            final int mesesEf = (totalEfectivos - aniosEf * 360) / 30;
            final int diasEf = totalEfectivos - aniosEf * 360 - mesesEf * 30;

            final String estadoRecordFinal = enAcumulacion ? ESTADO_EN_ACUMULACION : calc.estadoRecord();

            // FUENTE ÚNICA DE VERDAD: "Corresponden" y "Saldo" se leen directamente de la BD
            // (suma de DIAS_GANADOS/DIAS_GOZADOS de las filas ACTIVAS — ver ganadosPorEmp/
            // gozadosPorEmp, construidos con findByEmpleadoIdInAndActivo(...,1)). NO se calculan
            // al vuelo: eso ignoraría los períodos SIN_RECORD_LEGAL y las provisiones por
            // override manual. El Write Path (recálculo) ya garantiza que las filas malas se
            // anulan (activo=0) y no se infla nada, así que la simple suma refleja la realidad.
            content.add(new PadronVacacionalRowDto(
                    empId, f.getDni(), f.getNombreCompleto(), f.getRegimenLaboral(),
                    cargo, dependencia,
                    ts.anios(), ts.meses(), ts.dias(),
                    noComp.lsg(), noComp.faltas(), aniosEf, mesesEf, diasEf,
                    calc.diasCorresponden(), calc.diasGozados(), calc.saldo(),
                    estadoRecordFinal, false,
                    periodosPendientes, requiereDecision, tieneOverride));
        }

        return new PadronVacacionalPageDto(
                content, base.getTotalElements(), base.getTotalPages(),
                base.getPageNumber(), base.getPageSize());
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
    /**
     * Detalle de récord vacacional (Opción A): Nivel 1 = acumulado de la carrera (reconcilia con
     * Configuración Remunerativa/Vinculación); Nivel 2 = desglose por período (aniversario a
     * aniversario) con SUS incidencias y si cumple récord. Read-only, no toca el motor.
     */
    @Transactional(readOnly = true)
    public RecordVacacionalDetalleDto detalleRecord(Long empleadoId) {
        // Nivel 1 — acumulado (mismo criterio que VacacionService.calcularTiempoServicioDetalle).
        final TiempoServicioDetalleDto acumulado = acumuladoCarrera(empleadoId);

        // V012_55 — override más reciente (si existe), para el bloque "Trazabilidad del registro".
        final GoceRegistradoDto ultimoOverride = vacacionRepository
                .findByEmpleadoIdAndActivo(empleadoId, 1).stream()
                .filter(v -> VacacionService.ORIGEN_OVERRIDE_RRHH.equals(v.getOrigen()))
                .max(Comparator.comparing(
                        Vacacion::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(GoceRegistradoDto::from)
                .orElse(null);

        final List<EmpleadoPlanilla> vinculos =
                empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1);
        final LocalDate ingreso = vinculos.stream()
                .map(EmpleadoPlanilla::getFechaInicioContrato)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (ingreso == null) {
            return new RecordVacacionalDetalleDto(true, acumulado, List.of(), ultimoOverride);
        }

        final Map<Long, Integer> jornadaPorRegimen = jornadaRegimenRepository.findAll().stream()
                .filter(j -> j.getRegimenLaboralId() != null && j.getDiasSemana() != null)
                .collect(Collectors.toMap(
                        com.indeci.rrhh.entity.JornadaRegimen::getRegimenLaboralId,
                        com.indeci.rrhh.entity.JornadaRegimen::getDiasSemana, (a, b) -> a));
        final int jornada = resolverJornada(vinculoVigente(vinculos), jornadaPorRegimen);

        // Nivel 2 — un período por cada año de servicio CUMPLIDO (aniversario a aniversario).
        final long aniosCumplidos = ChronoUnit.YEARS.between(ingreso, LocalDate.now());
        final List<PeriodoRecordDto> periodos = new ArrayList<>();
        for (long n = 1; n <= aniosCumplidos; n++) {
            final LocalDate corte = ingreso.plusYears(n);
            final LocalDate desde = corte.minusYears(1);
            final LocalDate hasta = corte.minusDays(1);
            final int diasIdeal = Dias360.entre(desde, hasta) + 1;
            final DiasNoComputablesDto noComp =
                    incidenciaLaboralCompuesta.calcularDesglose(empleadoId, desde, hasta);
            final int efectivo = Math.max(0, diasIdeal - noComp.total());

            // Récord del período: mismo criterio que VacacionProvisionService (el
            // VacacionCalculoService descuenta las incidencias reales del período internamente).
            boolean ok = false;
            final Optional<com.indeci.rrhh.dto.TiempoServicioDto> tsOpt =
                    tiempoServicioService.calcularDesde(vinculos, empleadoId, corte);
            if (tsOpt.isPresent()) {
                final var ts = tsOpt.get();
                final VacacionCalculoDto calc = vacacionCalculoService.calcular(new VacacionCalculoInput(
                        empleadoId, desde, hasta, ts.anios(), ts.meses(), ts.dias(), diasIdeal,
                        0, 0, BigDecimal.ZERO, jornada, 0));
                ok = VacacionCalculoDto.RECORD_OK.equals(calc.estadoRecord());
            }
            periodos.add(new PeriodoRecordDto((int) n, desde, hasta,
                    noComp.lsg(), noComp.faltas(), noComp.suspensiones(), efectivo, ok, ok ? 30 : 0));
        }
        return new RecordVacacionalDetalleDto(false, acumulado, periodos, ultimoOverride);
    }

    /** Nivel 1 — réplica de VacacionService.calcularTiempoServicioDetalle (mismo origen que
     *  Configuración Remunerativa, para que el detalle reconcilie con Vinculación). */
    private TiempoServicioDetalleDto acumuladoCarrera(Long empleadoId) {
        final com.indeci.rrhh.dto.TiempoServicioDto ts;
        try {
            ts = tiempoServicioService.calcular(empleadoId, null);
        } catch (VinculoNoEncontradoException e) {
            return TiempoServicioDetalleDto.sinVinculo();
        }
        final DiasNoComputablesDto noComp = incidenciaLaboralCompuesta
                .calcularDesglose(empleadoId, ts.fechaIngreso(), ts.fechaCorte());
        final LocalDate aniversarioEfectivo = ts.fechaIngreso()
                .plusYears(ts.anios() + 1L).plusDays(noComp.total());
        final int totalDiasEfectivos = Math.max(0, ts.totalDias360() - noComp.total());
        final Dias360.AniosMesesDias efectivo = Dias360.desglosar(totalDiasEfectivos);
        return new TiempoServicioDetalleDto(ts, noComp, aniversarioEfectivo,
                efectivo.anios(), efectivo.meses(), efectivo.dias(), totalDiasEfectivos);
    }

    private int resolverJornada(EmpleadoPlanilla vigente, Map<Long, Integer> jornadaPorRegimen) {
        if (vigente == null) {
            return JORNADA_DEFECTO_DIAS;
        }
        if (vigente.getDiasSemanaOperativo() != null) {
            return vigente.getDiasSemanaOperativo();
        }
        final Integer delRegimen = vigente.getRegimenLaboralId() != null
                ? jornadaPorRegimen.get(vigente.getRegimenLaboralId())
                : null;
        return delRegimen != null ? delRegimen : JORNADA_DEFECTO_DIAS;
    }
}
