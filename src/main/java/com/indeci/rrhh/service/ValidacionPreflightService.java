package com.indeci.rrhh.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PreflightValidacionDto;
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
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;

import lombok.RequiredArgsConstructor;

/**
 * F3.3 — Centro de Validaciones (preflight de planilla).
 *
 * <p>Ejecuta 10 reglas de detección sobre el período seleccionado y devuelve
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
    private final TipoEventoRepository tipoEventoRepository;

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
        }

        return PreflightValidacionDto.desdeLista(periodo, hallazgos);
    }

    /**
     * Replica la lógica de
     * {@code GeneradorPlanillaService.regimenAplicaConcepto} sin reusar la
     * clase (mantiene a F3.3 aislado del motor). {@code null} o "TODOS"
     * aplica a cualquier régimen; valores CSV se evalúan por token.
     */
    private boolean regimenAplica(String regimenAplicable, String regimenEmpleado) {
        if (regimenAplicable == null || regimenAplicable.isBlank()) return true;
        if ("TODOS".equalsIgnoreCase(regimenAplicable)) return true;
        if (regimenEmpleado == null) return true; // sin régimen no se puede contradecir
        Set<String> tokens = new HashSet<>();
        for (String t : regimenAplicable.split(",")) {
            tokens.add(t.trim().toUpperCase());
        }
        return tokens.contains(regimenEmpleado.toUpperCase());
    }

    private String nombrePersona(Empleado e) {
        if (e == null || e.getPersonaId() == null) return null;
        Persona p = personaRepository.findById(e.getPersonaId()).orElse(null);
        return p == null ? null : p.getNombreCompleto();
    }
}
