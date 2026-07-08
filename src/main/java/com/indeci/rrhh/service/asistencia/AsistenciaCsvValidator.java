package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoMarcadorAlias;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoMarcadorAliasRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas de validación por fila del CSV del marcador (preview, sin persistir asistencia).
 */
@Component
@RequiredArgsConstructor
public class AsistenciaCsvValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AsistenciaCsvValidator.class);
    private static final String TABLA_DNI = "INDECI_PERSONA";

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final EmpleadoMarcadorAliasRepository aliasRepository;

    /** Compat: reportes con DNI (Reloj 1 diario). */
    public void validarFilas(List<MarcadorCsvRow> filas, PeriodoPlanilla periodo) {
        validarFilas(filas, periodo, FormatoMarcador.RELOJ1_DIARIO);
    }

    public void validarFilas(
            List<MarcadorCsvRow> filas, PeriodoPlanilla periodo, FormatoMarcador formato) {
        // Un mismo empleado (DNI/nombre) se repite ~30 veces (un día por fila). El biométrico de un
        // mes trae miles de filas pero solo cientos de trabajadores distintos. Sin caché, cada fila
        // dispara ~4 consultas a Oracle (persona/alias + empleados + activos + vínculos) → decenas de
        // miles de round-trips que sobre red lenta parecen "colgados". La caché por importación
        // memoiza la resolución por DNI/persona/empleado/alias: mismas consultas, una sola vez cada una.
        ResolucionCache cache = new ResolucionCache();
        for (MarcadorCsvRow fila : filas) {
            validarFila(fila, periodo, cache, formato);
        }
        marcarDuplicadosEnArchivo(filas);
    }

    /**
     * P1 / req 26 — Duplicidad empleado + fecha dentro del mismo archivo.
     * La primera ocurrencia se conserva; las siguientes se marcan ERROR para que
     * NO se confirmen automáticamente y no violen la unicidad de la tabla final.
     */
    private void marcarDuplicadosEnArchivo(List<MarcadorCsvRow> filas) {
        Set<String> vistos = new HashSet<>();
        for (MarcadorCsvRow fila : filas) {
            if (fila.getEmpleadoId() == null || fila.getFecha() == null
                    || "ERROR".equals(fila.getEstadoFila())) {
                continue;
            }
            String clave = fila.getEmpleadoId() + "|" + fila.getFecha();
            if (!vistos.add(clave)) {
                marcarError(fila, null, null,
                        "Fila duplicada para el mismo empleado y fecha dentro del archivo. "
                                + "Solo se conservará la primera ocurrencia.");
            }
        }
    }

    public void validarFila(MarcadorCsvRow fila, PeriodoPlanilla periodo) {
        // Uso individual (tests / caso puntual): caché de un solo uso, mismas consultas.
        validarFila(fila, periodo, new ResolucionCache(), FormatoMarcador.RELOJ1_DIARIO);
    }

    /**
     * Enruta según el formato: COEN sin DNI usa el camino por alias (SPEC D1); el
     * resto (Reloj 1 con DNI) usa la resolución clásica por DNI.
     */
    private void validarFila(
            MarcadorCsvRow fila, PeriodoPlanilla periodo, ResolucionCache cache, FormatoMarcador formato) {
        if (formato == FormatoMarcador.RELOJ2_COEN && esVacio(fila.getDni())) {
            validarFilaCoen(fila, periodo, cache);
            return;
        }
        validarFilaReloj1(fila, periodo, cache);
    }

    /**
     * Reporte COEN ("Reloj 2"): no trae DNI. La identidad se resuelve por la tabla
     * de alias (nombre normalizado → empleado). Sin alias → ERROR "SIN_MAPEO" (no
     * procesa, no descuenta) hasta que se mapee o se registre en Empleados (M03).
     */
    private void validarFilaCoen(MarcadorCsvRow fila, PeriodoPlanilla periodo, ResolucionCache cache) {
        if (fila.getFecha() == null) {
            marcarError(fila, null, null,
                    "Fecha inválida — use dd/MM/yyyy, d/M/yyyy o dd-MM-yyyy.");
            return;
        }
        if (fila.getFecha().isBefore(periodo.getFechaInicio())
                || fila.getFecha().isAfter(periodo.getFechaFin())) {
            marcarError(fila, null, null, "La fecha no pertenece al período seleccionado.");
            return;
        }
        String norm = NombreMarcadorNormalizer.normalizar(fila.getNombre());
        if (norm.isEmpty()) {
            marcarError(fila, null, null,
                    "Marca sin nombre de trabajador; no se puede identificar.");
            return;
        }
        Optional<EmpleadoMarcadorAlias> aliasOpt = cache.alias(norm);
        if (aliasOpt.isEmpty()) {
            marcarError(fila, null, null,
                    "SIN_MAPEO: el nombre \"" + fila.getNombre() + "\" no está asociado a ningún "
                            + "empleado. Mapéelo o regístrelo en Empleados (M03) antes de procesar.");
            return;
        }
        Long empleadoId = aliasOpt.get().getEmpleadoId();
        fila.setEmpleadoId(empleadoId);

        if (!vinculoVigenteEnFecha(empleadoId, fila.getFecha(), cache)) {
            marcarError(fila, null, empleadoId,
                    "La fecha de asistencia está fuera del vínculo laboral vigente del empleado "
                            + "(ingreso/cese). Verifique el contrato del periodo.");
            return;
        }
        validarObservacionMarcador(fila);
    }

    private void validarFilaReloj1(MarcadorCsvRow fila, PeriodoPlanilla periodo, ResolucionCache cache) {
        if (fila.getDni() == null || fila.getDni().length() != 8) {
            marcarError(fila, null, null, "DNI inválido — debe tener 8 dígitos.");
            return;
        }
        if (fila.getFecha() == null) {
            marcarError(fila, null, null,
                    "Fecha inválida — use dd/MM/yyyy, d/M/yyyy o dd-MM-yyyy.");
            return;
        }
        if (fila.getFecha().isBefore(periodo.getFechaInicio())
                || fila.getFecha().isAfter(periodo.getFechaFin())) {
            marcarError(fila, null, null, "La fecha no pertenece al período seleccionado.");
            return;
        }

        Optional<Persona> personaOpt = cache.persona(fila.getDni());
        if (personaOpt.isEmpty()) {
            marcarError(fila, null, null, "DNI no encontrado en INDECI_PERSONA.");
            return;
        }

        Persona persona = personaOpt.get();
        fila.setNombreSistema(persona.getNombreCompleto());

        ResolucionEmpleado resolucion = resolverEmpleado(persona, fila.getFecha(), cache);
        if (resolucion.tipo() == TipoResolucionEmpleado.SIN_EMPLEADO) {
            marcarError(fila, persona.getId(), null,
                    "DNI existe en persona, pero no tiene empleado asociado.");
            return;
        }
        if (resolucion.tipo() == TipoResolucionEmpleado.INACTIVO) {
            marcarError(fila, persona.getId(), null, "El empleado asociado no está activo.");
            return;
        }
        if (resolucion.tipo() == TipoResolucionEmpleado.AMBIGUO) {
            marcarError(fila, persona.getId(), null,
                    "Existen múltiples vínculos laborales vigentes para la fecha; requiere revisión manual.");
            return;
        }

        Empleado empleado = resolucion.empleado();
        fila.setEmpleadoId(empleado.getId());

        if (!vinculoVigenteEnFecha(empleado.getId(), fila.getFecha(), cache)) {
            marcarError(fila, persona.getId(), empleado.getId(),
                    "La fecha de asistencia está fuera del vínculo laboral vigente del empleado "
                            + "(ingreso/cese). Verifique el contrato del periodo.");
            return;
        }

        validarNombreMarcador(fila, persona);
        validarObservacionMarcador(fila);
        logDiagnosticoFila(fila, persona.getId(), empleado.getId(), persona.getNombreCompleto());
    }

    /**
     * F3 — Resuelve el empleado activo asociado a la persona.
     * Si hay varios ACTIVO, elige el único con vínculo planilla vigente en la fecha.
     */
    private ResolucionEmpleado resolverEmpleado(Persona persona, LocalDate fecha, ResolucionCache cache) {
        List<Empleado> todos = cache.empleados(persona.getId());
        if (todos.isEmpty()) {
            return ResolucionEmpleado.sinEmpleado();
        }

        List<Empleado> activos = cache.empleadosActivos(persona.getId());
        if (activos.isEmpty()) {
            return ResolucionEmpleado.inactivo();
        }
        if (activos.size() == 1) {
            return ResolucionEmpleado.ok(activos.get(0));
        }

        List<Empleado> vigentesEnFecha = activos.stream()
                .filter(e -> vinculoVigenteEnFecha(e.getId(), fecha, cache))
                .toList();
        if (vigentesEnFecha.size() == 1) {
            return ResolucionEmpleado.ok(vigentesEnFecha.get(0));
        }
        if (vigentesEnFecha.size() > 1) {
            return ResolucionEmpleado.ambiguo();
        }
        return ResolucionEmpleado.ok(activos.get(0));
    }

    /**
     * P5 / req 24 — Vínculo vigente a la fecha de asistencia.
     *
     * BRECHA conocida: el proyecto no tiene una tabla histórica dedicada de vínculo;
     * la vigencia se infiere de INDECI_EMPLEADO_PLANILLA (FECHA_INGRESO/FECHA_CESE).
     * Criterio conservador: solo se rechaza cuando hay datos que ubican la fecha
     * claramente fuera del rango. Si no hay registro de planilla o faltan fechas,
     * se acepta (no se puede determinar lo contrario) para no bloquear de más.
     */
    private boolean vinculoVigenteEnFecha(Long empleadoId, LocalDate fecha, ResolucionCache cache) {
        List<EmpleadoPlanilla> vinculos = cache.vinculosActivos(empleadoId);
        if (vinculos.isEmpty()) {
            return true;
        }
        for (EmpleadoPlanilla v : vinculos) {
            LocalDate ingreso = v.getFechaIngreso();
            LocalDate cese = v.getFechaCese();
            boolean despuesIngreso = ingreso == null || !fecha.isBefore(ingreso);
            boolean antesCese = cese == null || !fecha.isAfter(cese);
            if (despuesIngreso && antesCese) {
                return true;
            }
        }
        return false;
    }

    private void validarNombreMarcador(MarcadorCsvRow fila, Persona persona) {
        if (fila.getNombre() == null
                || persona.getNombreCompleto() == null
                || persona.getNombreCompleto().equalsIgnoreCase(fila.getNombre().trim())) {
            return;
        }
        fila.getAdvertencias().add("El nombre del marcador difiere del registrado en el sistema.");
        if ("VALIDA".equals(fila.getEstadoFila())) {
            fila.setEstadoFila("WARN");
        }
    }

    private void validarObservacionMarcador(MarcadorCsvRow fila) {
        String obs = fila.getObservacion() != null ? fila.getObservacion() : "";
        boolean marcaIncompleta = obs.toLowerCase().contains("marca incompleta");
        boolean sinMarcas = obs.isBlank()
                && !AsistenciaTiempoUtil.tieneMarca(fila.getMarca1())
                && !AsistenciaTiempoUtil.tieneMarca(fila.getMarca2());
        if ((marcaIncompleta || sinMarcas) && "VALIDA".equals(fila.getEstadoFila())) {
            fila.setEstadoFila("OBSERVADA");
        }
    }

    private void marcarError(MarcadorCsvRow fila, Long personaId, Long empleadoId, String mensaje) {
        fila.setEstadoFila("ERROR");
        fila.getErrores().add(mensaje);
        logDiagnosticoFila(fila, personaId, empleadoId, fila.getNombreSistema());
    }

    private static boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    private void logDiagnosticoFila(
            MarcadorCsvRow fila, Long personaId, Long empleadoId, String nombrePersona) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        String mensaje = fila.getErrores().isEmpty()
                ? (fila.getAdvertencias().isEmpty() ? "" : String.join("; ", fila.getAdvertencias()))
                : String.join("; ", fila.getErrores());
        LOG.debug(
                "validarFila nroLinea={} dniNormalizado={} tablaUsadaParaBuscarDni={} "
                        + "personaIdEncontrado={} empleadoIdEncontrado={} nombrePersonaSistema={} "
                        + "fechaParseada={} estadoValidacion={} mensajeValidacion={}",
                fila.getNumeroFila(),
                fila.getDni(),
                TABLA_DNI,
                personaId,
                empleadoId,
                nombrePersona,
                fila.getFecha(),
                fila.getEstadoFila(),
                mensaje);
    }

    /**
     * Caché por importación que memoiza la resolución de identidad (persona → empleados →
     * vínculos) reutilizando exactamente los mismos métodos de repositorio. Cada clave
     * (DNI, personaId, empleadoId) consulta a Oracle a lo sumo una vez, aunque el mismo
     * empleado aparezca en decenas de filas del mes. No es thread-safe: vive dentro de una
     * sola llamada a {@link #validarFilas} (misma transacción, un solo hilo).
     */
    private final class ResolucionCache {

        private final Map<String, Optional<Persona>> personas = new HashMap<>();
        private final Map<Long, List<Empleado>> empleadosPorPersona = new HashMap<>();
        private final Map<Long, List<Empleado>> activosPorPersona = new HashMap<>();
        private final Map<Long, List<EmpleadoPlanilla>> vinculosPorEmpleado = new HashMap<>();
        private final Map<String, Optional<EmpleadoMarcadorAlias>> aliasPorNombre = new HashMap<>();

        Optional<Persona> persona(String dni) {
            return personas.computeIfAbsent(dni, personaRepository::findByDniNormalizado);
        }

        Optional<EmpleadoMarcadorAlias> alias(String nombreNorm) {
            return aliasPorNombre.computeIfAbsent(
                    nombreNorm, n -> aliasRepository.findFirstByNombreMarcadorNormAndActivo(n, 1));
        }

        List<Empleado> empleados(Long personaId) {
            return empleadosPorPersona.computeIfAbsent(
                    personaId, empleadoRepository::findAllByPersonaId);
        }

        List<Empleado> empleadosActivos(Long personaId) {
            return activosPorPersona.computeIfAbsent(
                    personaId, id -> empleadoRepository.findAllByPersonaIdAndEstado(id, "ACTIVO"));
        }

        List<EmpleadoPlanilla> vinculosActivos(Long empleadoId) {
            return vinculosPorEmpleado.computeIfAbsent(
                    empleadoId, id -> empleadoPlanillaRepository.findByEmpleadoIdAndActivo(id, 1));
        }
    }

    private enum TipoResolucionEmpleado {
        OK, SIN_EMPLEADO, INACTIVO, AMBIGUO
    }

    private record ResolucionEmpleado(TipoResolucionEmpleado tipo, Empleado empleado) {

        static ResolucionEmpleado ok(Empleado empleado) {
            return new ResolucionEmpleado(TipoResolucionEmpleado.OK, empleado);
        }

        static ResolucionEmpleado sinEmpleado() {
            return new ResolucionEmpleado(TipoResolucionEmpleado.SIN_EMPLEADO, null);
        }

        static ResolucionEmpleado inactivo() {
            return new ResolucionEmpleado(TipoResolucionEmpleado.INACTIVO, null);
        }

        static ResolucionEmpleado ambiguo() {
            return new ResolucionEmpleado(TipoResolucionEmpleado.AMBIGUO, null);
        }
    }
}
