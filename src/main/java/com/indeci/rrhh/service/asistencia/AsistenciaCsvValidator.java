package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Reglas de validación por fila del CSV del marcador (preview, sin persistir asistencia).
 */
@Component
@RequiredArgsConstructor
public class AsistenciaCsvValidator {

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;

    public void validarFilas(List<MarcadorCsvRow> filas, PeriodoPlanilla periodo) {
        for (MarcadorCsvRow fila : filas) {
            validarFila(fila, periodo);
        }
    }

    public void validarFila(MarcadorCsvRow fila, PeriodoPlanilla periodo) {
        if (fila.getDni() == null || fila.getDni().length() != 8) {
            marcarError(fila, "DNI inválido — debe tener 8 dígitos.");
            return;
        }
        if (fila.getFecha() == null) {
            marcarError(fila, "Fecha inválida — use dd/MM/yyyy.");
            return;
        }
        if (fila.getFecha().isBefore(periodo.getFechaInicio())
                || fila.getFecha().isAfter(periodo.getFechaFin())) {
            marcarError(fila, "La fecha no pertenece al período seleccionado.");
            return;
        }

        Optional<Persona> personaOpt = personaRepository.findByDni(fila.getDni());
        if (personaOpt.isEmpty()) {
            marcarError(fila, "Empleado no registrado en el sistema (DNI no encontrado).");
            return;
        }

        Persona persona = personaOpt.get();
        fila.setNombreSistema(persona.getNombreCompleto());

        Optional<Empleado> empleadoOpt = empleadoRepository.findByPersonaId(persona.getId());
        if (empleadoOpt.isEmpty() || !"ACTIVO".equalsIgnoreCase(empleadoOpt.get().getEstado())) {
            marcarError(fila, "No existe vínculo de empleado activo para el DNI.");
            return;
        }
        fila.setEmpleadoId(empleadoOpt.get().getId());

        validarNombreMarcador(fila, persona);
        validarObservacionMarcador(fila);
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

    private void marcarError(MarcadorCsvRow fila, String mensaje) {
        fila.setEstadoFila("ERROR");
        fila.getErrores().add(mensaje);
    }
}
