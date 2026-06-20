package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PreflightValidacionDto;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import com.indeci.rrhh.dto.ValidacionHallazgoDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.EventoDistribucionMesRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;
import com.indeci.rrhh.service.support.RegimenAplicableHelper;

import lombok.RequiredArgsConstructor;

/**
 * F3.3 — Centro de Validaciones (preflight de planilla).
 *
 * <p>Ejecuta 14 reglas de detección sobre el período seleccionado y devuelve
 * un {@link PreflightValidacionDto} con los hallazgos agrupados por severidad
 * (BLOQUEO / ALERTA / INFO). NO modifica datos: es solo lectura.</p>
 *
 * <h3>Reglas</h3>
 * <pre>
 *  V1  BLOQUEO  Período existe y está abierto                       SPEC §5.2
 *  V2  BLOQUEO  Período con NRO_CERT_PRESUP                         LEY-05
 *  V3  BLOQUEO  Asistencia del período en estado VALIDADA           LEY-04
 *  V4  BLOQUEO  Empleados activos sin EmpleadoPlanilla configurada  SPEC §5.2
 *  V5  ALERTA   Empleados sin régimen pensionario vigente           §3
 *  V6  BLOQUEO  EmpleadoConcepto activos con concepto sin MEF       LEY-01
 *  V7  BLOQUEO  EmpleadoConcepto con régimen no aplicable           F1.5b
 *  V8  INFO     Empleados en ESTADO_LABORAL=EN_TRANSICION (CAS→728) LEY-06
 *  V9  ALERTA   Conceptos sin TIPO_CONCEPTO MEF                     LEY-01 / Spec 013
 *  V10 ALERTA   Eventos del período (subsidio) sin documento adjunto F2.4
 *  V11–V14      IR 4ta categoría CAS (ver evaluarIr4taCas)          FASE 1
 *  V15 ALERTA   Activo con planilla pero SIN sueldo básico          FIX base remun.
 *  V16 BLOQUEO  Falta TASA_ESSALUD o ESSALUD_MINIMO del año         P1 EsSalud CAS
 *  V17 ALERTA   Falta TOPE_ESSALUD_PCT_UIT (CAS sin tope de base)   P1 EsSalud CAS
 *  V18 ALERTA   Subsidio (GENERA_SUBSIDIO='S') que NO resta días    Fase 4 subsidio→neto
 *  V19 ALERTA   Maternidad multi-mes: subsidio al neto diferido P1   P0 maternidad
 * </pre>
 *
 * <p>El servicio prioriza no romper si los datos están incompletos
 * (no lanza al detectar problemas — los reporta como hallazgos).</p>
 */
@Service
@RequiredArgsConstructor
public class ValidacionPreflightService {

    private static final String EMP_ACTIVO = "ACTIVO";
    private static final String ASIST_VALIDADA = "VALIDADA";
    private static final String EN_TRANSICION = "EN_TRANSICION";

    private final PeriodoPlanillaRepository periodoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final EmpleadoPensionRepository pensionRepository;
    private final EmpleadoConceptoRepository empleadoConceptoRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final AsistenciaCabeceraRepository asistenciaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final EmpleadoEventoRepository empleadoEventoRepository;
    private final EventoDistribucionMesRepository eventoDistribucionMesRepository;
    private final TipoEventoRepository tipoEventoRepository;
    // FASE 1 — IR 4ta CAS (V11–V14).
    private final ParametroRemunerativoService parametroService;
    private final Suspension4taService suspension4taService;

    private static final String REG_LABORAL_CAS = "CAS";
    private static final String CODIGO_INTERNO_IR4TA_CAS = "IR4TA_CAS";
    private static final String TRIBUTO_SUNAT_IR4TA = "3042";

    public PreflightValidacionDto evaluar(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            throw new NegocioException("Selecciona un período válido (YYYY-MM).");
        }

        List<ValidacionHallazgoDto> hallazgos = new ArrayList<>();

        // V1 / V2 — período
        Optional<PeriodoPlanilla> periodoOpt =
                periodoRepository.findByPeriodoAndActivo(periodo, 1);
        if (periodoOpt.isEmpty()) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V1", "Período",
                    "El período " + periodo + " no existe o está inactivo. Créalo antes de continuar.",
                    null, null, null));
            return PreflightValidacionDto.desdeLista(periodo, hallazgos);
        }
        PeriodoPlanilla per = periodoOpt.get();
        if ("CERRADO".equalsIgnoreCase(per.getEstado())) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V1", "Período",
                    "El período " + periodo + " está CERRADO. No se puede generar planilla.",
                    null, null, per.getId()));
        }
        if (per.getNroCertPresup() == null || per.getNroCertPresup().isBlank()) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V2", "Período",
                    "Falta número de certificación presupuestal (LEY-05).",
                    null, null, per.getId()));
        }

        // Caches para evitar N queries por empleado
        Map<Long, String> nombresEmpleado = new HashMap<>();
        Map<Long, String> regimenPorEmpleado = new HashMap<>();

        // Universo de empleados ACTIVOS (V4 / V5 / V6 / V7 / V8)
        List<Empleado> activos = empleadoRepository.findByEstado(EMP_ACTIVO);
        for (Empleado e : activos) {
            nombresEmpleado.put(e.getId(), nombrePersona(e));
        }

        // V4 — activos sin EmpleadoPlanilla configurada
        Map<Long, EmpleadoPlanilla> planillaPorEmpleado = new HashMap<>();
        for (EmpleadoPlanilla pl : planillaRepository.findByActivo(1)) {
            planillaPorEmpleado.put(pl.getEmpleadoId(), pl);
        }
        for (Empleado e : activos) {
            if (!planillaPorEmpleado.containsKey(e.getId())) {
                hallazgos.add(ValidacionHallazgoDto.bloqueo(
                        "V4", "Empleado",
                        "Empleado activo sin configuración de planilla.",
                        e.getId(), nombresEmpleado.get(e.getId()), null));
            }
        }

        // V15 — activo CON planilla pero SIN sueldo básico (null o 0).
        //   Cinturón de seguridad post-backfill V010_54: desde el fix de base
        //   remunerativa, el ingreso base se toma de EmpleadoPlanilla.sueldoBasico
        //   (ya NO de un concepto manual). Si quedó null/0, la planilla saldría
        //   con ingreso 0 sin avisar. ALERTA (no BLOQUEO): puede ser legítimo
        //   (licencia sin goce / suspensión), pero debe revisarse.
        for (Empleado e : activos) {
            EmpleadoPlanilla pl = planillaPorEmpleado.get(e.getId());
            if (pl != null && (pl.getSueldoBasico() == null || pl.getSueldoBasico() <= 0)) {
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V15", "Empleado",
                        "Empleado activo con configuración de planilla pero SIN sueldo básico "
                                + "(la base remunerativa se toma de la configuración de planilla). "
                                + "Verifica el sueldo básico o el ingreso saldría en 0.",
                        e.getId(),
                        nombresEmpleado.get(e.getId()),
                        pl.getId()));
            }
        }

        // Resolver régimen laboral por empleado (cache para V7)
        Map<Long, String> regimenLabPorId = new HashMap<>();
        for (RegimenLaboral rl : regimenLaboralRepository.findAll()) {
            regimenLabPorId.put(rl.getId(), rl.getCodigo());
        }
        for (EmpleadoPlanilla pl : planillaPorEmpleado.values()) {
            if (pl.getRegimenLaboralId() != null) {
                String cod = regimenLabPorId.get(pl.getRegimenLaboralId());
                if (cod != null) regimenPorEmpleado.put(pl.getEmpleadoId(), cod);
            }
        }

        // V5 — sin régimen pensionario vigente
        for (Empleado e : activos) {
            if (!pensionRepository.existsByEmpleadoIdAndActivo(e.getId(), 1)) {
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V5", "Empleado",
                        "Empleado sin régimen pensionario vigente.",
                        e.getId(), nombresEmpleado.get(e.getId()), null));
            }
        }

        // V8 — ESTADO_LABORAL = EN_TRANSICION (LEY-06)
        for (EmpleadoPlanilla pl : planillaPorEmpleado.values()) {
            if (EN_TRANSICION.equalsIgnoreCase(pl.getEstadoLaboral())) {
                hallazgos.add(ValidacionHallazgoDto.info(
                        "V8", "Empleado",
                        "Empleado en transición CAS→728: el motor sigue calculándolo como CAS (LEY-06).",
                        pl.getEmpleadoId(),
                        nombresEmpleado.get(pl.getEmpleadoId()),
                        pl.getId()));
            }
        }

        // V3 — asistencia del período validada (al menos una cabecera VALIDADA)
        List<AsistenciaCabecera> asistencias =
                asistenciaRepository.findByPeriodoAndActivo(periodo, 1);
        long validadas = asistencias.stream()
                .filter(a -> ASIST_VALIDADA.equalsIgnoreCase(a.getEstado()))
                .count();
        if (asistencias.isEmpty()) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V3", "Asistencia",
                    "No hay asistencia registrada para el período. Cárgala antes de generar planilla.",
                    null, null, null));
        } else if (validadas == 0) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V3", "Asistencia",
                    "La asistencia del período no está validada. Apruébala antes de generar planilla.",
                    null, null, null));
        }

        // V6 / V7 — EmpleadoConcepto y régimen aplicable
        // Pre-cargo todos los conceptos para evitar N queries.
        Map<Long, ConceptoPlanilla> conceptoPorId = new HashMap<>();
        for (ConceptoPlanilla c : conceptoRepository.findByActivo(1)) {
            conceptoPorId.put(c.getId(), c);
        }
        for (Empleado e : activos) {
            String regEmp = regimenPorEmpleado.get(e.getId());
            List<EmpleadoConcepto> ecs = empleadoConceptoRepository
                    .findByEmpleadoIdAndActivo(e.getId(), 1);
            for (EmpleadoConcepto ec : ecs) {
                ConceptoPlanilla c = conceptoPorId.get(ec.getConceptoPlanillaId());
                if (c == null) continue;

                if (c.getCodigoMef() == null || c.getCodigoMef().isBlank()) {
                    hallazgos.add(ValidacionHallazgoDto.bloqueo(
                            "V6", "Concepto",
                            "Concepto '" + c.getNombre() + "' asignado al empleado sin CODIGO_MEF (LEY-01).",
                            e.getId(), nombresEmpleado.get(e.getId()), ec.getId()));
                }
                if (regEmp != null && !regimenAplica(c.getRegimenAplicable(), regEmp)) {
                    hallazgos.add(ValidacionHallazgoDto.bloqueo(
                            "V7", "Concepto",
                            "Concepto '" + c.getNombre() + "' no aplica al régimen " + regEmp
                                    + " del empleado (F1.5b).",
                            e.getId(), nombresEmpleado.get(e.getId()), ec.getId()));
                }
            }
        }

        // V9 — Conceptos catalogados sin TIPO_CONCEPTO
        for (ConceptoPlanilla c : conceptoPorId.values()) {
            if (c.getTipoConcepto() == null || c.getTipoConcepto().isBlank()) {
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V9", "Concepto",
                        "Concepto '" + c.getNombre() + "' sin TIPO_CONCEPTO MEF. Clasifícalo para PLAME.",
                        null, null, c.getId()));
            }
        }

        // V10 — Eventos del período con TipoEvento.requiereAdjunto='S' sin sustento
        Map<Long, TipoEvento> tipoEventoPorId = new HashMap<>();
        for (TipoEvento te : tipoEventoRepository.findAll()) {
            tipoEventoPorId.put(te.getId(), te);
        }
        for (EmpleadoEvento ev : empleadoEventoRepository.findByPeriodoAndActivo(periodo, 1)) {
            TipoEvento tipo = tipoEventoPorId.get(ev.getTipoEventoId());
            if (tipo == null) continue;
            if ("S".equalsIgnoreCase(tipo.getRequiereAdjunto())
                    && ev.getSustentoLegajoDocId() == null) {
                if (!nombresEmpleado.containsKey(ev.getEmpleadoId())) {
                    Empleado e = empleadoRepository.findById(ev.getEmpleadoId()).orElse(null);
                    nombresEmpleado.put(ev.getEmpleadoId(), e != null ? nombrePersona(e) : null);
                }
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V10", "Evento",
                        "Evento '" + tipo.getNombre() + "' sin documento de sustento adjunto.",
                        ev.getEmpleadoId(),
                        nombresEmpleado.get(ev.getEmpleadoId()),
                        ev.getId()));
            }

            // P0-F0: tipos GENERA_SUBSIDIO retirados del flujo operativo (V010_90).
            // V18/V19 solo aplicaban al motor legacy por eventos; se omiten para legacy.
            if ("S".equalsIgnoreCase(tipo.getGeneraSubsidio())) {
                continue;
            }

            // V19 — Maternidad que cruza meses: imputación al neto diferida (P1).
            if ("MATERNIDAD".equalsIgnoreCase(tipo.getCodigo())
                    && eventoDistribucionMesRepository
                            .countDistinctPeriodosByEmpleadoEventoId(ev.getId()) > 1) {
                if (!nombresEmpleado.containsKey(ev.getEmpleadoId())) {
                    Empleado e = empleadoRepository.findById(ev.getEmpleadoId()).orElse(null);
                    nombresEmpleado.put(ev.getEmpleadoId(), e != null ? nombrePersona(e) : null);
                }
                long periodos = eventoDistribucionMesRepository
                        .countDistinctPeriodosByEmpleadoEventoId(ev.getId());
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V19", "Evento",
                        "Evento maternidad ID " + ev.getId() + " cruza " + periodos
                                + " periodos. Subsidio al neto diferido hasta imputación "
                                + "mensual (P1).",
                        ev.getEmpleadoId(),
                        nombresEmpleado.get(ev.getEmpleadoId()),
                        ev.getId()));
            }

            // V18 — Subsidio mal configurado: GENERA_SUBSIDIO='S' pero
            //   AFECTA_DIAS_LABORADOS='N'. El motor NO suma el subsidio al neto
            //   (el sueldo no se prorrateó), para evitar doble pago. Se alerta a
            //   RR. HH. (no bloquea): hay que marcar el tipo como que resta días.
            if ("S".equalsIgnoreCase(tipo.getGeneraSubsidio())
                    && !"S".equalsIgnoreCase(tipo.getAfectaDiasLaborados())) {
                if (!nombresEmpleado.containsKey(ev.getEmpleadoId())) {
                    Empleado e = empleadoRepository.findById(ev.getEmpleadoId()).orElse(null);
                    nombresEmpleado.put(ev.getEmpleadoId(), e != null ? nombrePersona(e) : null);
                }
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V18", "Evento",
                        "Evento '" + tipo.getNombre() + "' genera subsidio pero su tipo NO "
                                + "resta días laborados: el subsidio NO se sumará al neto "
                                + "(se evita doble pago). Marca AFECTA_DIAS_LABORADOS='S' si "
                                + "corresponde pagarlo por planilla.",
                        ev.getEmpleadoId(),
                        nombresEmpleado.get(ev.getEmpleadoId()),
                        ev.getId()));
            }
        }

        // V11–V14 — IR 4ta categoría CAS (FASE 1).
        evaluarIr4taCas(periodo, activos, regimenPorEmpleado, planillaPorEmpleado,
                nombresEmpleado, hallazgos);

        // V16 / V17 — Parámetros EsSalud (P1 validación EsSalud CAS).
        evaluarEssaludCas(periodo, activos, regimenPorEmpleado, planillaPorEmpleado, hallazgos);

        return PreflightValidacionDto.desdeLista(periodo, hallazgos);
    }

    /**
     * FASE 1 — Validaciones de la retención IR 4ta CAS.
     *
     * <p>Solo se evalúa si existe al menos un empleado CAS activo con
     * remuneración configurada (sueldo &gt; 0) — i.e. un caso que podría
     * retener. Severidades según el criterio del cliente:</p>
     * <pre>
     *  V11 ALERTA   CAS con base > inafecto SIN constancia de suspensión cargada
     *  V12 ALERTA   CAS con constancia de suspensión 4ta VENCIDA
     *  V13 BLOQUEO  Falta parámetro TASA_IR4TA / BASE_INAFECTA_IR4TA (técnico)
     *  V14 BLOQUEO  Concepto IR4TA_CAS ausente o sin CODIGO_TRIBUTO_SUNAT=3042 (técnico)
     * </pre>
     *
     * <p><b>NO</b> se bloquea por ausencia de CODIGO_MEF: IR4TA_CAS es una
     * retención tributaria SUNAT (3042), no un concepto de ingreso MEF/AIRHSP.</p>
     */
    private void evaluarIr4taCas(
            String periodo,
            List<Empleado> activos,
            Map<Long, String> regimenPorEmpleado,
            Map<Long, EmpleadoPlanilla> planillaPorEmpleado,
            Map<Long, String> nombresEmpleado,
            List<ValidacionHallazgoDto> hallazgos) {

        List<Empleado> casConRemuneracion = activos.stream()
                .filter(e -> REG_LABORAL_CAS.equalsIgnoreCase(regimenPorEmpleado.get(e.getId())))
                .filter(e -> {
                    EmpleadoPlanilla pl = planillaPorEmpleado.get(e.getId());
                    return pl != null && pl.getSueldoBasico() != null && pl.getSueldoBasico() > 0;
                })
                .toList();

        if (casConRemuneracion.isEmpty()) {
            return; // No hay CAS que pueda retener 4ta: nada que validar.
        }

        int anio = ParametroRemunerativoService.periodoToFechaInicio(periodo).getYear();
        LocalDate devengue = ParametroRemunerativoService.periodoToFechaInicio(periodo);

        // V13 — parámetros tributarios presentes (técnico, BLOQUEO).
        Optional<BigDecimal> baseInafecta =
                parametroService.obtenerValorOpcional("BASE_INAFECTA_IR4TA", anio, null);
        Optional<BigDecimal> tasa =
                parametroService.obtenerValorOpcional("TASA_IR4TA", anio, null);
        if (tasa.isEmpty()) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V13", "Parámetro",
                    "Falta el parámetro TASA_IR4TA del año " + anio
                            + ". Cárgalo antes de generar planilla CAS.",
                    null, null, null));
        }
        if (baseInafecta.isEmpty()) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V13", "Parámetro",
                    "Falta el parámetro BASE_INAFECTA_IR4TA del año " + anio
                            + ". Cárgalo antes de generar planilla CAS.",
                    null, null, null));
        }

        // V14 — concepto IR4TA_CAS configurado con tributo SUNAT 3042 (técnico, BLOQUEO).
        Optional<ConceptoPlanilla> conceptoIr4ta =
                conceptoRepository.findByCodigoAndActivo(CODIGO_INTERNO_IR4TA_CAS, 1);
        if (conceptoIr4ta.isEmpty()) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V14", "Concepto",
                    "No existe el concepto IR4TA_CAS activo. Ejecutar el seed "
                            + "V010_49 (retención IR 4ta CAS, tributo SUNAT 3042).",
                    null, null, null));
        } else if (!TRIBUTO_SUNAT_IR4TA.equals(conceptoIr4ta.get().getCodigoTributoSunat())) {
            hallazgos.add(ValidacionHallazgoDto.bloqueo(
                    "V14", "Concepto",
                    "El concepto IR4TA_CAS no tiene CODIGO_TRIBUTO_SUNAT=3042. "
                            + "Corrígelo (no usar CODIGO_MEF para la retención tributaria).",
                    null, null, conceptoIr4ta.get().getId()));
        }

        // V11 / V12 — por empleado (solo si el umbral inafecto está disponible).
        if (baseInafecta.isEmpty()) {
            return;
        }
        BigDecimal umbral = baseInafecta.get();
        for (Empleado e : casConRemuneracion) {
            BigDecimal base = BigDecimal.valueOf(
                    planillaPorEmpleado.get(e.getId()).getSueldoBasico());
            if (base.compareTo(umbral) <= 0) {
                continue; // inafecto: no retiene, no requiere constancia.
            }
            Suspension4taVigenteDto susp =
                    suspension4taService.consultarVigente(e.getId(), devengue);
            if (susp == null || susp.vigente()) {
                continue; // suspensión vigente (o sin dato fiable): no se alerta.
            }
            if (susp.existeVencida()) {
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V12", "Empleado",
                        "CAS con constancia de suspensión de 4ta VENCIDA: se retendrá 8%. "
                                + "Renueva la constancia SUNAT si corresponde.",
                        e.getId(), nombresEmpleado.get(e.getId()), null));
            } else {
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V11", "Empleado",
                        "CAS con base > inafecto y SIN constancia de suspensión de 4ta: "
                                + "se retendrá 8%. Verifica si tiene suspensión SUNAT.",
                        e.getId(), nombresEmpleado.get(e.getId()), null));
            }
        }
    }

    /**
     * Replica la lógica de
     * {@code GeneradorPlanillaService.regimenAplicaConcepto} sin reusar la
     * clase (mantiene a F3.3 aislado del motor). {@code null} o "TODOS"
     * aplica a cualquier régimen; valores CSV se evalúan por token.
     */
    /**
     * P1 EsSalud CAS — V16 / V17.
     *
     * <pre>
     *  V16 BLOQUEO  TASA_ESSALUD o ESSALUD_MINIMO faltante: el motor lanzará al intentar calcular.
     *  V17 ALERTA   TOPE_ESSALUD_PCT_UIT faltante para CAS: el motor es defensivo (no topea la base).
     * </pre>
     */
    private void evaluarEssaludCas(
            String periodo,
            List<Empleado> activos,
            Map<Long, String> regimenPorEmpleado,
            Map<Long, EmpleadoPlanilla> planillaPorEmpleado,
            List<ValidacionHallazgoDto> hallazgos) {

        if (planillaPorEmpleado.isEmpty()) return;

        int anio = ParametroRemunerativoService.periodoToFechaInicio(periodo).getYear();

        // V16 — TASA_ESSALUD y ESSALUD_MINIMO son requeridos (obtenerValor lanza si faltan).
        parametroService.obtenerValorOpcional("TASA_ESSALUD", anio, null).ifPresentOrElse(
                t -> { /* OK */ },
                () -> hallazgos.add(ValidacionHallazgoDto.bloqueo(
                        "V16", "Parámetro",
                        "Falta el parámetro TASA_ESSALUD del año " + anio
                                + ". El motor no puede calcular el aporte EsSalud empleador.",
                        null, null, null)));

        parametroService.obtenerValorOpcional("ESSALUD_MINIMO", anio, null).ifPresentOrElse(
                m -> { /* OK */ },
                () -> hallazgos.add(ValidacionHallazgoDto.bloqueo(
                        "V16", "Parámetro",
                        "Falta el parámetro ESSALUD_MINIMO del año " + anio
                                + ". El motor no puede aplicar el piso regulatorio (9% RMV).",
                        null, null, null)));

        // V17 — TOPE_ESSALUD_PCT_UIT es opcional pero sin él el tope CAS no se aplica.
        boolean hayCas = activos.stream()
                .anyMatch(e -> REG_LABORAL_CAS.equalsIgnoreCase(regimenPorEmpleado.get(e.getId()))
                        && planillaPorEmpleado.containsKey(e.getId()));

        if (hayCas) {
            Optional<BigDecimal> tope =
                    parametroService.obtenerValorOpcional("TOPE_ESSALUD_PCT_UIT", anio, null);
            if (tope.isEmpty() || tope.get().signum() == 0) {
                hallazgos.add(ValidacionHallazgoDto.alerta(
                        "V17", "Parámetro",
                        "Falta el parámetro TOPE_ESSALUD_PCT_UIT del año " + anio
                                + ". La base EsSalud de trabajadores CAS se calculará "
                                + "sin tope (se usará la base completa).",
                        null, null, null));
            }
        }
    }

    private boolean regimenAplica(String regimenAplicable, String regimenEmpleado) {
        // Fuente única de la regla (incluye alias CAS≡1057). Ver RegimenAplicableHelper.
        return RegimenAplicableHelper.aplica(regimenAplicable, regimenEmpleado);
    }

    private String nombrePersona(Empleado e) {
        if (e == null || e.getPersonaId() == null) return null;
        Persona p = personaRepository.findById(e.getPersonaId()).orElse(null);
        return p == null ? null : p.getNombreCompleto();
    }
}
