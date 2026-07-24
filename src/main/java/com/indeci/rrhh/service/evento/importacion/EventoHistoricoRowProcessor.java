package com.indeci.rrhh.service.evento.importacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.Estado;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.FilaResultadoDto;

import lombok.RequiredArgsConstructor;

/**
 * V012_42 F2 — procesa UNA fila del Excel histórico: resuelve DNI→empleado y MOTIVO→tipo de
 * evento, valida fechas/idempotencia/solape, e inserta si todo está correcto.
 *
 * <p><b>Transacción por fila</b> ({@link Propagation#REQUIRES_NEW}, mismo patrón que
 * {@code VinculacionUpsertService}): si una fila falla (DNI inexistente, solape, error de BD),
 * solo esa fila se descarta — las demás 453 siguen su curso. Nunca lanza: toda fila termina en
 * un {@link FilaResultadoDto} con estado OK / DUPLICADO_OMITIDO / ERROR.</p>
 */
@Service
@RequiredArgsConstructor
public class EventoHistoricoRowProcessor {

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final TipoEventoRepository tipoEventoRepository;
    private final EmpleadoEventoRepository empleadoEventoRepository;
    private final EventoHistoricoMotivoMapper motivoMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FilaResultadoDto procesar(EventoHistoricoRowRaw fila) {
        final String dni = fila.digitos(EventoHistoricoColumna.DNI);
        final String nombre = fila.texto(EventoHistoricoColumna.SERVIDOR);
        final String motivoExcel = fila.texto(EventoHistoricoColumna.MOTIVO);

        try {
            final Long empleadoId = resolverEmpleadoId(dni);
            if (empleadoId == null) {
                return rechazo(fila, dni, nombre, motivoExcel,
                        "DNI '" + dni + "' no coincide con ningún empleado registrado.");
            }

            final String codigoTipo = motivoMapper.resolverCodigo(motivoExcel);
            if (codigoTipo == null) {
                return rechazo(fila, dni, nombre, motivoExcel,
                        "Motivo '" + motivoExcel + "' no tiene mapeo a un tipo de evento conocido.");
            }
            final TipoEvento tipo = tipoEventoRepository.findByCodigo(codigoTipo).orElse(null);
            if (tipo == null || tipo.getActivo() == null || tipo.getActivo() != 1) {
                return rechazo(fila, dni, nombre, motivoExcel,
                        "Tipo de evento '" + codigoTipo + "' no existe o está inactivo en el catálogo.");
            }

            final LocalDate fechaInicio = fila.fecha(EventoHistoricoColumna.FECHA_INICIO);
            final LocalDate fechaFin = fila.fecha(EventoHistoricoColumna.FECHA_FIN);
            if (fechaInicio == null || fechaFin == null) {
                return rechazo(fila, dni, nombre, motivoExcel,
                        "Fecha de inicio o término ilegible.");
            }
            if (fechaFin.isBefore(fechaInicio)) {
                return rechazo(fila, dni, nombre, motivoExcel,
                        "La fecha de término es anterior a la fecha de inicio.");
            }

            // Idempotencia — mismo UK que usa el alta manual y la materialización desde papeleta
            // (EMPLEADO_ID, TIPO_EVENTO_ID, FECHA_INICIO). Re-ejecutar el import no duplica.
            if (empleadoEventoRepository.existsByEmpleadoIdAndTipoEventoIdAndFechaInicioAndActivo(
                    empleadoId, tipo.getId(), fechaInicio, 1)) {
                return new FilaResultadoDto(fila.getNumeroFila(), dni, nombre, motivoExcel,
                        Estado.DUPLICADO_OMITIDO,
                        "Ya existe un evento igual (mismo empleado, tipo y fecha de inicio).");
            }

            // Solape — mismo validador que EventoPeriodoService.validarSinSolape (F2.5); ninguno
            // de los tipos históricos permite solape (PERMITE_SOLAPE='N').
            final List<EmpleadoEvento> solapados = empleadoEventoRepository.findSolapados(
                    empleadoId, fechaInicio, fechaFin, null);
            if (!solapados.isEmpty()) {
                final EmpleadoEvento primero = solapados.get(0);
                return rechazo(fila, dni, nombre, motivoExcel,
                        "Solapa con el evento ID " + primero.getId() + " ("
                                + primero.getFechaInicio() + " → " + primero.getFechaFin() + ").");
            }

            final Integer diasAfectos = fila.entero(EventoHistoricoColumna.TOTAL_DIAS);
            insertar(empleadoId, tipo, fechaInicio, fechaFin, diasAfectos, fila, motivoExcel);

            return new FilaResultadoDto(fila.getNumeroFila(), dni, nombre, motivoExcel, Estado.OK, null);
        } catch (RuntimeException e) {
            return rechazo(fila, dni, nombre, motivoExcel, "No se pudo procesar: " + e.getMessage());
        }
    }

    private void insertar(
            Long empleadoId, TipoEvento tipo, LocalDate fechaInicio, LocalDate fechaFin,
            Integer diasAfectos, EventoHistoricoRowRaw fila, String motivoExcel) {
        final EmpleadoEvento e = new EmpleadoEvento();
        e.setEmpleadoId(empleadoId);
        e.setTipoEventoId(tipo.getId());
        e.setPeriodo(String.format("%04d%02d", fechaInicio.getYear(), fechaInicio.getMonthValue()));
        e.setFechaInicio(fechaInicio);
        e.setFechaFin(fechaFin);
        e.setDiasAfectos(diasAfectos != null
                ? diasAfectos
                : (int) (java.time.temporal.ChronoUnit.DAYS.between(fechaInicio, fechaFin) + 1));
        e.setObservacion(observacion(fila, motivoExcel));
        e.setEstado("VALIDADO"); // histórico migrado — ya ocurrió y RR.HH. lo entrega como cerrado.
        e.setActivo(1);
        e.setCreatedAt(java.time.LocalDateTime.now());
        e.setCreatedBy("IMPORT_HISTORICO_TIEMPO_SERVICIO");
        empleadoEventoRepository.save(e);
    }

    /**
     * N° de resolución en OBSERVACION (pedido explícito). Si el motivo original era "SANCION PAD"
     * (agrupado bajo SUSPENSION_HISTORICA por decisión RR.HH.), se antepone para no perder el
     * rastro del texto original — auditoría.
     */
    private String observacion(EventoHistoricoRowRaw fila, String motivoExcel) {
        final String resolucion = fila.texto(EventoHistoricoColumna.N_RESOLUCION);
        final StringBuilder sb = new StringBuilder("Importado de histórico RR.HH.");
        if ("SANCION PAD".equalsIgnoreCase(motivoExcel)) {
            sb.append(" — Motivo original: SANCION PAD.");
        }
        if (resolucion != null && !resolucion.isBlank()) {
            sb.append(" N° Resolución: ").append(resolucion).append(".");
        }
        return sb.toString();
    }

    /** DNI → Persona (normalizado) → Empleado; prefiere ACTIVO si hay más de un registro. */
    private Long resolverEmpleadoId(String dni) {
        if (dni == null || dni.isBlank()) {
            return null;
        }
        final Optional<Persona> persona = personaRepository.findByDniNormalizado(dni);
        if (persona.isEmpty()) {
            return null;
        }
        final List<Empleado> empleados = empleadoRepository.findAllByPersonaId(persona.get().getId());
        if (empleados.isEmpty()) {
            return null;
        }
        return empleados.stream()
                .filter(e -> "ACTIVO".equalsIgnoreCase(e.getEstado()))
                .findFirst()
                .orElse(empleados.get(0))
                .getId();
    }

    private FilaResultadoDto rechazo(
            EventoHistoricoRowRaw fila, String dni, String nombre, String motivoExcel, String mensaje) {
        return new FilaResultadoDto(fila.getNumeroFila(), dni, nombre, motivoExcel, Estado.ERROR, mensaje);
    }
}
