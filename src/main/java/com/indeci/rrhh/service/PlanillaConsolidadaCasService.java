package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.dto.export.PlanillaConsolidadaRowDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.CalculoSnapshot;
import com.indeci.rrhh.entity.CondicionLaboral;
import com.indeci.rrhh.entity.Department;
import com.indeci.rrhh.entity.District;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoBanco;
import com.indeci.rrhh.entity.EmpleadoPension;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.Province;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.entity.Sexo;
import com.indeci.rrhh.entity.Suspension4ta;
import com.indeci.rrhh.entity.TipoComisionAfp;
import com.indeci.rrhh.entity.TipoContrato;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.CalculoSnapshotRepository;
import com.indeci.rrhh.repository.CondicionLaboralRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.DepartmentRepository;
import com.indeci.rrhh.repository.DistrictRepository;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.ProvinceRepository;
import com.indeci.rrhh.repository.RegimenPensionarioRepository;
import com.indeci.rrhh.repository.SexoRepository;
import com.indeci.rrhh.repository.Suspension4taRepository;
import com.indeci.rrhh.repository.TipoComisionAfpRepository;
import com.indeci.rrhh.repository.TipoContratoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0 — Construye las filas de la Planilla CAS Consolidada cruzando 9 entidades
 * con carga en batch (sin N+1). NO calcula nada: solo lee resultados ya
 * generados por el motor y los snapshots de trazabilidad.
 *
 * <p>Las columnas que el sistema aún no captura quedan {@code null}; el writer
 * las exporta como celda vacía (spec SPEC_PLANILLA_CAS_EXPORT_COLUMNS).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanillaConsolidadaCasService {

    // Códigos SISPER de descuentos de terceros (spec bloque 14).
    private static final String SIS_JUDICIAL   = "716";
    private static final String SIS_REHAB      = "735";
    private static final String SIS_OTROS_DESC = "717";
    private static final String SIS_FESALUD    = "734";
    private static final String SIS_SURA       = "703";
    private static final String SIS_MASVIDA    = "715";
    private static final String SIS_SANMIGUEL  = "727";
    private static final String SIS_SERFINCO   = "726";
    private static final String SIS_RIMAC      = "714";
    private static final String SIS_COPAGO_EPS = "725";

    private final MovimientoPlanillaRepository        movimientoRepo;
    private final MovimientoPlanillaDetalleRepository detalleRepo;
    private final ConceptoPlanillaRepository          conceptoRepo;
    private final EmpleadoRepository                  empleadoRepo;
    private final PersonaRepository                   personaRepo;
    private final EmpleadoPlanillaRepository          planillaRepo;
    private final EmpleadoBancoRepository             bancoRepo;
    private final EmpleadoPensionRepository           pensionRepo;
    private final Suspension4taRepository             suspensionRepo;
    private final AsistenciaCabeceraRepository        asistenciaRepo;
    private final CalculoSnapshotRepository           snapshotRepo;
    private final PeriodoPlanillaRepository           periodoRepo;
    // Catálogos
    private final SexoRepository            sexoRepo;
    private final EstadoCivilRepository     estadoCivilRepo;
    private final DistrictRepository        districtRepo;
    private final ProvinceRepository        provinceRepo;
    private final DepartmentRepository      departmentRepo;
    private final BankRepository            bankRepo;
    private final RegimenPensionarioRepository regimenPensionarioRepo;
    private final TipoComisionAfpRepository tipoComisionAfpRepo;
    private final CondicionLaboralRepository condicionLaboralRepo;
    private final TipoContratoRepository    tipoContratoRepo;
    // Writer + auditoría de exportación
    private final PlanillaCasExcelWriter    excelWriter;
    private final com.indeci.rrhh.repository.ExportArchivoRepository exportRepo;

    private static final String TIPO_CAS = "XLSX_CAS_CONSOLIDADA";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Genera el XLSX de la Planilla CAS Consolidada y registra su historial
     * (auditoría: tipo, período, líneas, hash). El historial nunca bloquea la
     * entrega del archivo.
     */
    @Transactional(readOnly = true)
    public byte[] generarYRegistrar(String periodo) {
        List<PlanillaConsolidadaRowDto> filas = construir(periodo);
        byte[] bytes = excelWriter.escribir(periodo, filas);
        guardarHistorial(periodo, filas.size(), bytes);
        return bytes;
    }

    private void guardarHistorial(String periodo, int nroFilas, byte[] bytes) {
        try {
            com.indeci.rrhh.entity.ExportArchivo registro = new com.indeci.rrhh.entity.ExportArchivo();
            registro.setPeriodo(periodo);
            registro.setTipoArchivo(TIPO_CAS);
            registro.setNombreArchivo("planilla-cas-consolidada-" + periodo + ".xlsx");
            registro.setHashSha256(sha256(bytes));
            registro.setNroLineas(nroFilas);
            registro.setFechaGenerado(java.time.LocalDateTime.now());
            exportRepo.save(registro);
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar historial de Planilla CAS Consolidada {}: {}",
                    periodo, ex.getMessage());
        }
    }

    private static String sha256(byte[] data) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "no-hash";
        }
    }

    @Transactional(readOnly = true)
    public List<PlanillaConsolidadaRowDto> construir(String periodo) {
        List<MovimientoPlanilla> movs = movimientoRepo.findByPeriodoAndActivo(periodo, 1);
        if (movs.isEmpty()) return Collections.emptyList();

        movs.sort((a, b) -> {
            Long ea = a.getEmpleadoId(), eb = b.getEmpleadoId();
            return Objects.compare(ea, eb, Long::compareTo);
        });

        List<Long> empIds = movs.stream()
                .map(MovimientoPlanilla::getEmpleadoId).distinct().toList();
        List<Long> movIds = movs.stream().map(MovimientoPlanilla::getId).toList();

        // ── Batch loading (9 grupos de queries) ──────────────────────────────
        Map<Long, Empleado> empleadoPorId = empleadoRepo.findAllById(empIds).stream()
                .collect(Collectors.toMap(Empleado::getId, e -> e, (a, b) -> a));

        List<Long> personaIds = empleadoPorId.values().stream()
                .map(Empleado::getPersonaId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Persona> personaPorId = personaRepo.findAllById(personaIds).stream()
                .collect(Collectors.toMap(Persona::getId, p -> p, (a, b) -> a));

        Map<Long, EmpleadoPlanilla> planillaPorEmp =
                planillaRepo.findByEmpleadoIdInAndActivo(empIds, 1).stream()
                        .collect(Collectors.toMap(EmpleadoPlanilla::getEmpleadoId, p -> p, (a, b) -> a));

        Map<Long, EmpleadoBanco> bancoPorEmp =
                bancoRepo.findByEmpleadoIdInAndEsCuentaPlanillaAndActivo(empIds, 1, 1).stream()
                        .collect(Collectors.toMap(EmpleadoBanco::getEmpleadoId, b -> b, (a, b) -> a));

        Map<Long, EmpleadoPension> pensionPorEmp =
                pensionRepo.findByEmpleadoIdInAndActivo(empIds, 1).stream()
                        .collect(Collectors.toMap(EmpleadoPension::getEmpleadoId, p -> p, (a, b) -> a));

        LocalDate inicioPeriodo = parsePeriodoInicio(periodo);
        LocalDate finPeriodo = inicioPeriodo.withDayOfMonth(inicioPeriodo.lengthOfMonth());
        Map<Long, Suspension4ta> suspensionPorEmp =
                suspensionRepo.findVigentesEnPeriodo(empIds, inicioPeriodo, finPeriodo).stream()
                        .collect(Collectors.toMap(Suspension4ta::getEmpleadoId, s -> s, (a, b) -> a));

        Map<Long, AsistenciaCabecera> asistenciaPorEmp =
                asistenciaRepo.findByPeriodoAndActivo(periodo, 1).stream()
                        .collect(Collectors.toMap(AsistenciaCabecera::getEmpleadoId, a -> a, (a, b) -> a));

        Map<Long, List<MovimientoPlanillaDetalle>> detallesPorMov =
                detalleRepo.findByMovimientoPlanillaIdIn(movIds).stream()
                        .collect(Collectors.groupingBy(MovimientoPlanillaDetalle::getMovimientoPlanillaId));

        Map<Long, List<CalculoSnapshot>> snapshotsPorEmp =
                snapshotRepo.findByEmpleadoIdInAndPeriodoAndActivo(empIds, periodo, 1).stream()
                        .collect(Collectors.groupingBy(CalculoSnapshot::getEmpleadoId));

        Map<Long, ConceptoPlanilla> conceptoPorId = conceptoRepo.findByActivo(1).stream()
                .collect(Collectors.toMap(ConceptoPlanilla::getId, c -> c, (a, b) -> a));

        // ── Catálogos en memoria ─────────────────────────────────────────────
        Map<Long, String> sexoNombre = sexoRepo.findAll().stream()
                .collect(Collectors.toMap(Sexo::getId, Sexo::getNombre, (a, b) -> a));
        Map<Long, String> estadoCivilNombre = estadoCivilRepo.findAll().stream()
                .collect(Collectors.toMap(EstadoCivil::getId, EstadoCivil::getNombre, (a, b) -> a));
        Map<String, District> districtPorId = districtRepo.findAll().stream()
                .collect(Collectors.toMap(District::getId, d -> d, (a, b) -> a));
        Map<String, Province> provincePorId = provinceRepo.findAll().stream()
                .collect(Collectors.toMap(Province::getId, p -> p, (a, b) -> a));
        Map<String, Department> departmentPorId = departmentRepo.findAll().stream()
                .collect(Collectors.toMap(Department::getId, d -> d, (a, b) -> a));
        Map<Long, String> bankNombre = bankRepo.findAll().stream()
                .collect(Collectors.toMap(Bank::getId, Bank::getName, (a, b) -> a));
        Map<Long, RegimenPensionario> regimenPensPorId = regimenPensionarioRepo.findAll().stream()
                .collect(Collectors.toMap(RegimenPensionario::getId, r -> r, (a, b) -> a));
        Map<Long, String> tipoComisionNombre = tipoComisionAfpRepo.findAll().stream()
                .collect(Collectors.toMap(TipoComisionAfp::getId, TipoComisionAfp::getNombre, (a, b) -> a));
        Map<Long, String> condicionNombre = condicionLaboralRepo.findAll().stream()
                .collect(Collectors.toMap(CondicionLaboral::getId, CondicionLaboral::getNombre, (a, b) -> a));
        Map<Long, String> tipoContratoNombre = tipoContratoRepo.findAll().stream()
                .collect(Collectors.toMap(TipoContrato::getId, TipoContrato::getNombre, (a, b) -> a));

        String estadoPeriodo = periodoRepo.findAll().stream()
                .filter(p -> periodo.equals(p.getPeriodo()))
                .map(PeriodoPlanilla::getEstado)
                .findFirst().orElse(null);

        // ── Construcción de filas ────────────────────────────────────────────
        List<PlanillaConsolidadaRowDto> filas = new ArrayList<>(movs.size());
        int nro = 1;
        for (MovimientoPlanilla mov : movs) {
            Long empId = mov.getEmpleadoId();
            Empleado emp = empleadoPorId.get(empId);
            Persona persona = (emp != null && emp.getPersonaId() != null)
                    ? personaPorId.get(emp.getPersonaId()) : null;

            Ctx ctx = new Ctx();
            ctx.nro = nro++;
            ctx.mov = mov;
            ctx.emp = emp;
            ctx.persona = persona;
            ctx.planilla = planillaPorEmp.get(empId);
            ctx.banco = bancoPorEmp.get(empId);
            ctx.pension = pensionPorEmp.get(empId);
            ctx.suspension = suspensionPorEmp.get(empId);
            ctx.asistencia = asistenciaPorEmp.get(empId);
            ctx.detalles = detallesPorMov.getOrDefault(mov.getId(), List.of());
            ctx.snapshots = snapshotsPorEmp.getOrDefault(empId, List.of());
            ctx.estadoPeriodo = estadoPeriodo;
            ctx.sexoNombre = sexoNombre;
            ctx.estadoCivilNombre = estadoCivilNombre;
            ctx.districtPorId = districtPorId;
            ctx.provincePorId = provincePorId;
            ctx.departmentPorId = departmentPorId;
            ctx.bankNombre = bankNombre;
            ctx.regimenPensPorId = regimenPensPorId;
            ctx.tipoComisionNombre = tipoComisionNombre;
            ctx.condicionNombre = condicionNombre;
            ctx.tipoContratoNombre = tipoContratoNombre;
            ctx.conceptoPorId = conceptoPorId;

            filas.add(buildRow(ctx));
        }
        log.info("Planilla CAS Consolidada {} — {} filas construidas", periodo, filas.size());
        return filas;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Construcción de una fila
    // ═════════════════════════════════════════════════════════════════════════

    private PlanillaConsolidadaRowDto buildRow(Ctx c) {
        PlanillaConsolidadaRowDto r = new PlanillaConsolidadaRowDto();
        r.setNro(c.nro);

        // BLOQUE 1 — Datos personales
        if (c.persona != null) {
            r.setDni(c.persona.getDni());
            r.setRuc(c.persona.getRuc());
            r.setApellidosNombres(c.persona.getNombreCompleto());
            LocalDate fnac = toLocalDate(c.persona.getFechaNacimiento());
            r.setFechaNacimiento(fnac);
            r.setEdad(fnac != null ? Period.between(fnac, LocalDate.now()).getYears() : null);
            r.setSexo(c.sexoNombre.get(c.persona.getSexoId()));
            r.setEstadoCivil(c.estadoCivilNombre.get(c.persona.getEstadoCivilId()));
            r.setPersonaId(c.persona.getId());
            r.setCelular(c.persona.getTelefono());
            r.setCorreo(c.persona.getEmail());
            r.setDireccion(c.persona.getDireccion());
            resolverUbigeo(c, r, c.persona.getDistritoId());
        }

        // BLOQUE 2 — Datos laborales
        if (c.emp != null) {
            r.setCodigoSisper(c.emp.getCodigoSisper());
            r.setRegistroAirhsp(c.emp.getRegistroAirhsp());
            r.setMontoAirhsp(toBd(c.emp.getAirhspMonto()));
        }
        if (c.planilla != null) {
            r.setEstadoLaboral(c.planilla.getEstadoLaboral());
            r.setCondicionLaboral(c.condicionNombre.get(c.planilla.getCondicionLaboralId()));
            r.setFechaIngreso(c.planilla.getFechaIngreso());
            r.setFechaTermino(c.planilla.getFechaCese());
        }

        // BLOQUE 3 — Banco / Abono
        if (c.banco != null) {
            r.setBanco(c.bankNombre.get(c.banco.getBankId()));
            r.setNumeroCuenta(c.banco.getNumeroCuenta());
            r.setCci(c.banco.getCci());
            r.setNumeroCuentaTesoreria(c.banco.getNumeroCuentaTesoreria());
            r.setEstadoCuentaBancaria(unoCero(c.banco.getActivo(), "ACTIVO", "INACTIVO"));
            r.setCuentaPrincipal(unoCero(c.banco.getEsCuentaPlanilla(), "SÍ", "NO"));
            r.setFechaRegistroCuenta(c.banco.getFechaInicio());
        }

        // BLOQUE 4 — Régimen pensionario
        if (c.pension != null) {
            RegimenPensionario rp = c.regimenPensPorId.get(c.pension.getRegimenPensionarioId());
            String tipo = c.pension.getTipoRegimen();
            if (rp != null) {
                r.setRegimenPensionario(rp.getNombre());
                if ("AFP".equalsIgnoreCase(rp.getTipo())) r.setAfp(rp.getNombre());
                if ("ONP".equalsIgnoreCase(rp.getTipo())) r.setOnp("ONP");
                r.setPensionistaSinRegimenRetiro(rp.getTipo());
            }
            if (r.getRegimenPensionario() == null && tipo != null) r.setRegimenPensionario(tipo);
            r.setTipoComision(c.tipoComisionNombre.get(c.pension.getTipoComisionAfpId()));
            r.setComisionPct(toBd(c.pension.getPorcentajeComision()));
            r.setCuspp(c.pension.getCuspp());
            r.setEstadoConfigPension(unoCero(c.pension.getActivo(), "CONFIGURADO", "INACTIVO"));
            r.setFechaInicioRegPension(c.pension.getFechaInicio());
        }

        // BLOQUE 5 — Suspensión 4ta
        if (c.suspension != null) {
            r.setFechaSuspension(c.suspension.getFechaVigIni());
            r.setNroOperacion(c.suspension.getNroConstancia());
            r.setSuspensionFlag(1);
            r.setSuspensionRenta4ta("CON SUSPENSIÓN");
            r.setFechaEmisionConstancia(c.suspension.getFechaEmision());
            r.setFechaInicioVigencia(c.suspension.getFechaVigIni());
            r.setFechaFinVigencia(c.suspension.getFechaVigFin());
            r.setEstadoSuspension(c.suspension.getEstado());
            r.setObservacionSuspension(c.suspension.getObservacion());
        } else {
            r.setSuspensionFlag(0);
            r.setSuspensionRenta4ta("SIN SUSPENSIÓN");
        }

        // BLOQUE 6 — Presupuesto (parcial: lo que existe en EmpleadoPlanilla)
        if (c.planilla != null) {
            r.setMeta2026(c.planilla.getMeta());
            r.setCentroCosto2026(c.planilla.getCentroCosto());
            r.setFuenteFinanciamiento(c.planilla.getFuenteFinanciamiento());
        }

        // BLOQUE 7 — Contrato / Plaza (parcial)
        if (c.planilla != null) {
            r.setNumeroContrato(c.planilla.getNumeroContrato());
            r.setCondicionLaboralContrato(c.condicionNombre.get(c.planilla.getCondicionLaboralId()));
            r.setFechaIngresoContrato(c.planilla.getFechaIngreso());
            r.setFechaTerminoContrato(c.planilla.getFechaCese());
            r.setMontoContrato(toBd(c.planilla.getSueldoBasico()));
            r.setEstadoContrato(c.planilla.getEstadoLaboral());
            r.setTipoContrato(c.tipoContratoNombre.get(c.planilla.getTipoContratoId()));
        }

        // BLOQUE 8 — Remuneración mensual (montos directos del movimiento/detalle)
        if (c.planilla != null) r.setMontoContratoRem(toBd(c.planilla.getSueldoBasico()));
        if (c.asistencia != null) r.setDiasLaborados(c.asistencia.getDiasLaborados());
        BigDecimal pagoDif = sumarDetalle(c.detalles, MovimientoPlanillaDetalle::getPagoDiferencial);
        r.setPagoDiferencial(nonZero(pagoDif));
        r.setDiasReintegro(sumarEnteroDetalle(c.detalles, MovimientoPlanillaDetalle::getDiasReintegro));
        r.setMontoReintegro(nonZero(sumarDetalle(c.detalles, MovimientoPlanillaDetalle::getMontoReintegro)));
        r.setTotalRemuneracionMensual(toBd(c.mov.getTotalIngresos()));

        // BLOQUE 9 — Asistencia
        if (c.asistencia != null) {
            r.setDiasNoLaborados(c.asistencia.getDiasFalta());
            r.setReduccionTardanzas(toBd(c.asistencia.getDescuentoTardanza()));
            r.setInasistenciaFaltas(toBd(c.asistencia.getDescuentoFalta()));
            r.setMinutosTardanza(c.asistencia.getTotalMinTardanza());
            r.setDiasDescuentoInasistencia(c.asistencia.getDiasFalta());
            r.setObservacionAsistencia(c.asistencia.getObservacion());
            r.setDescuentoTotalAsistencia(sumaNullSafe(
                    toBd(c.asistencia.getDescuentoTardanza()), toBd(c.asistencia.getDescuentoFalta())));
        }

        // BLOQUE 10/11/12/13 — desde snapshots + detalle
        aplicarSnapshots(c, r);
        aplicarEssaludDetalle(c, r);

        // BLOQUE 14 — Descuentos terceros (filtro por código SISPER)
        Map<String, BigDecimal> porSisper = montosPorSisper(c);
        r.setDescuentoJudicial(porSisper.get(SIS_JUDICIAL));
        r.setCooperativaRehabilitadora(porSisper.get(SIS_REHAB));
        r.setOtrosDescuentos(porSisper.get(SIS_OTROS_DESC));
        r.setFesalud(porSisper.get(SIS_FESALUD));
        r.setInterSura(porSisper.get(SIS_SURA));
        r.setMasVidaEssalud(porSisper.get(SIS_MASVIDA));
        r.setCoopSanMiguel(porSisper.get(SIS_SANMIGUEL));
        r.setCoopSerfinco(porSisper.get(SIS_SERFINCO));
        r.setRimacSeguros(porSisper.get(SIS_RIMAC));
        r.setCopagoEpsDescuento(porSisper.get(SIS_COPAGO_EPS));
        r.setTotalDescuentosTerceros(sumaValores(porSisper));

        // BLOQUE 15 — Totales / Neto
        r.setTotalDescuento(toBd(c.mov.getTotalDescuentos()));
        r.setNetoPagar(toBd(c.mov.getNetoPagar()));
        r.setRemuneracionBrutaBlq15(toBd(c.mov.getTotalIngresos()));
        r.setTotalDescuentosTercerosBlq15(r.getTotalDescuentosTerceros());
        r.setTotalDescuentosJudiciales(r.getDescuentoJudicial());

        // BLOQUE 16 — Validación 50%
        r.setCincuentaPctRemuneracion(toBd(c.mov.getNeto50pctMinimo()));
        r.setEstado50pct(c.mov.getEstadoNeto());
        r.setDescuentoJudicialConsiderado(r.getDescuentoJudicial());

        // BLOQUE 18 — Comparativo (solo neto actual en P0)
        r.setNetoActual(toBd(c.mov.getNetoPagar()));

        // BLOQUE 19 — Observaciones / Auditoría
        r.setObservacionPlanilla(c.mov.getObservacion());
        r.setEstadoPeriodo(c.estadoPeriodo);
        r.setEstadoRegistro(c.mov.getEstado());

        return r;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Snapshots → bases imponibles, EsSalud, IR4ta
    // ═════════════════════════════════════════════════════════════════════════

    private void aplicarSnapshots(Ctx c, PlanillaConsolidadaRowDto r) {
        CalculoSnapshot general = snapshot(c, "GENERAL");
        CalculoSnapshot essalud = snapshot(c, "ESSALUD");
        CalculoSnapshot ir4ta   = snapshot(c, "IR4TA_CAS");

        if (general != null) {
            r.setBaseImponibleIngresosEgresos(general.getBaseCalculo());
            Map<String, Object> j = parseJson(general.getParametrosJson());
            r.setBaseAfpOnp(bd(j.get("baseAsegurable")));
            r.setBaseAfp(bd(j.get("baseAfp")));
            r.setBaseOnp(bd(j.get("baseOnp")));
        }
        r.setRemuneracionBruta(toBd(c.mov.getTotalIngresos()));
        r.setBaseNetaValidacion50(toBd(c.mov.getNeto50pctMinimo()));

        if (essalud != null) {
            r.setBaseEssalud(essalud.getBaseCalculo());
            r.setEssaludCalculado(essalud.getResultado());
            r.setBaseEssaludBlq17(essalud.getBaseCalculo());
            Map<String, Object> j = parseJson(essalud.getParametrosJson());
            r.setTope45pctUit(bd(j.get("topeAplicado")));
            r.setEssaludMinimo(bd(j.get("essaludMinimo")));
            r.setTope45pctUitBlq17(bd(j.get("topeAplicado")));
            r.setEssaludMinimoBlq17(bd(j.get("essaludMinimo")));
            Object estado = j.get("estado");
            if (estado != null) {
                r.setEstadoEssalud(estado.toString());
                r.setEstadoEssaludBlq17(estado.toString());
            }
        }

        if (ir4ta != null) {
            r.setBaseIr4ta(ir4ta.getBaseCalculo());
            r.setIr4taRemuneracion(ir4ta.getResultado());
            r.setIr4taTotal(ir4ta.getResultado());
            Map<String, Object> j = parseJson(ir4ta.getParametrosJson());
            r.setBaseInafectaMensualIr4ta(bd(j.get("baseInafecta")));
            r.setTasaIr4ta(bd(j.get("tasa")));
            r.setCodigoTributoSunat("3042");
        }

        // Indicador suspensión (bloque 12)
        if (c.suspension != null) {
            r.setIndicadorSuspension("SUSPENDIDO");
            r.setNroOperacionSuspension(c.suspension.getNroConstancia());
            r.setEstadoSuspension4ta(c.suspension.getEstado());
        } else if (ir4ta != null && ir4ta.getResultado() != null
                && ir4ta.getResultado().signum() > 0) {
            r.setIndicadorSuspension("RETENIDO");
        } else {
            r.setIndicadorSuspension("INAFECTO");
        }
    }

    private void aplicarEssaludDetalle(Ctx c, PlanillaConsolidadaRowDto r) {
        BigDecimal essalud675 = sumarDetalle(c.detalles, MovimientoPlanillaDetalle::getEssalud675);
        BigDecimal copagoEps = sumarDetalle(c.detalles, MovimientoPlanillaDetalle::getCopagoEps);
        r.setEssalud675pct(nonZero(essalud675));
        r.setEps225pct(nonZero(copagoEps));
        r.setCopagoEpsTrabajador(nonZero(copagoEps));
        BigDecimal aporte = sumaNullSafe(essalud675, copagoEps);
        r.setAporteEssalud(nonZero(aporte));
        // IR5ta total (bloque relacionado) — informativo desde detalle
        BigDecimal ir5taTotal = sumarDetalle(c.detalles, MovimientoPlanillaDetalle::getIr5taTotal);
        if (ir5taTotal != null && ir5taTotal.signum() != 0) {
            r.setTotalDescuentosLegales(ir5taTotal); // base informativa; el motor es la fuente oficial
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private Map<String, BigDecimal> montosPorSisper(Ctx c) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (MovimientoPlanillaDetalle d : c.detalles) {
            if (d.getMonto() == null || d.getMonto() == 0d) continue;
            ConceptoPlanilla cp = c.conceptoPorId.get(d.getConceptoPlanillaId());
            if (cp == null || cp.getCodigoSisper() == null) continue;
            m.merge(cp.getCodigoSisper(), BigDecimal.valueOf(d.getMonto()), BigDecimal::add);
        }
        return m;
    }

    private CalculoSnapshot snapshot(Ctx c, String regla) {
        return c.snapshots.stream()
                .filter(s -> regla.equals(s.getRegla()))
                .findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static BigDecimal sumarDetalle(List<MovimientoPlanillaDetalle> dets,
                                           java.util.function.Function<MovimientoPlanillaDetalle, Double> f) {
        double s = 0d;
        boolean any = false;
        for (MovimientoPlanillaDetalle d : dets) {
            Double v = f.apply(d);
            if (v != null) { s += v; any = true; }
        }
        return any ? BigDecimal.valueOf(s) : null;
    }

    private static Integer sumarEnteroDetalle(List<MovimientoPlanillaDetalle> dets,
                                              java.util.function.Function<MovimientoPlanillaDetalle, Integer> f) {
        int s = 0;
        boolean any = false;
        for (MovimientoPlanillaDetalle d : dets) {
            Integer v = f.apply(d);
            if (v != null) { s += v; any = true; }
        }
        return any ? s : null;
    }

    private static BigDecimal sumaValores(Map<String, BigDecimal> m) {
        if (m.isEmpty()) return null;
        return m.values().stream().filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumaNullSafe(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    private static BigDecimal nonZero(BigDecimal v) {
        return (v == null || v.signum() == 0) ? null : v;
    }

    private void resolverUbigeo(Ctx c, PlanillaConsolidadaRowDto r, String distritoId) {
        if (distritoId == null) return;
        District d = c.districtPorId.get(distritoId);
        if (d == null) return;
        r.setDistrito(d.getName());
        Province p = c.provincePorId.get(d.getProvinceId());
        if (p == null) return;
        r.setProvincia(p.getName());
        Department dep = c.departmentPorId.get(p.getDepartmentId());
        if (dep != null) r.setDepartamento(dep.getName());
    }

    private static String unoCero(Integer flag, String siUno, String siCero) {
        if (flag == null) return null;
        return flag == 1 ? siUno : siCero;
    }

    private static BigDecimal toBd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }

    private static BigDecimal bd(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate toLocalDate(java.util.Date d) {
        return d == null ? null : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static LocalDate parsePeriodoInicio(String periodo) {
        // periodo "YYYY-MM" → primer día del mes
        String[] parts = periodo.split("-");
        int y = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return LocalDate.of(y, m, 1);
    }

    // Contexto de construcción de una fila (evita métodos con 25 parámetros).
    private static final class Ctx {
        Integer nro;
        MovimientoPlanilla mov;
        Empleado emp;
        Persona persona;
        EmpleadoPlanilla planilla;
        EmpleadoBanco banco;
        EmpleadoPension pension;
        Suspension4ta suspension;
        AsistenciaCabecera asistencia;
        List<MovimientoPlanillaDetalle> detalles;
        List<CalculoSnapshot> snapshots;
        String estadoPeriodo;
        Map<Long, String> sexoNombre;
        Map<Long, String> estadoCivilNombre;
        Map<String, District> districtPorId;
        Map<String, Province> provincePorId;
        Map<String, Department> departmentPorId;
        Map<Long, String> bankNombre;
        Map<Long, RegimenPensionario> regimenPensPorId;
        Map<Long, String> tipoComisionNombre;
        Map<Long, String> condicionNombre;
        Map<Long, String> tipoContratoNombre;
        Map<Long, ConceptoPlanilla> conceptoPorId;
    }
}
