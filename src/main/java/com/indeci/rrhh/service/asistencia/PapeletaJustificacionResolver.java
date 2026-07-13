package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Concilia los días que caerían en FALTA con las papeletas APROBADAS por RR. HH.
 * que justifican la asistencia (con goce + teletrabajo, flag JUSTIFICA_ASISTENCIA).
 *
 * <p>Regla de negocio (decisión RR. HH.):
 * <ul>
 *   <li>Papeleta de TELETRABAJO aprobada que cubre la fecha → día {@code TELETRABAJO}
 *       (trabajo efectivo remoto, Ley 31572): cuenta como laborado, no descuenta.</li>
 *   <li>Papeleta/permiso CON GOCE aprobado que cubre la fecha → día {@code PERMISO}:
 *       justificado, no descuenta (no cuenta como laborado).</li>
 *   <li>Sin papeleta justificante → el día permanece como {@code FALTA} (descuenta).</li>
 * </ul>
 *
 * <p>La clasificación con goce / sin goce NO está hardcodeada (REGLA-02): se lee del
 * flag {@code JUSTIFICA_ASISTENCIA} de {@code INDECI_TIPO_SOLICITUD_RRHH} (V012_36).
 */
@Component
@RequiredArgsConstructor
public class PapeletaJustificacionResolver {

    /** ESTADO_SOLICITUD_ID = 9 → APROBADA. */
    private static final long ESTADO_SOLICITUD_APROBADA = 9L;
    private static final String CODIGO_TELETRABAJO = "TELETRABAJO";
    static final String TIPO_DIA_TELETRABAJO = "TELETRABAJO";
    static final String TIPO_DIA_PERMISO = "PERMISO";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SolicitudRrhhRepository solicitudRrhhRepository;

    /**
     * Papeletas aprobadas y justificantes del empleado que podrían cubrir el período
     * (una sola consulta por empleado). Devuelve lista vacía si no hay datos válidos.
     */
    public List<SolicitudRrhh> cargarJustificantes(Long empleadoId, LocalDate finPeriodo) {
        if (empleadoId == null || finPeriodo == null) {
            return List.of();
        }
        return solicitudRrhhRepository.findJustificantesAsistencia(
                empleadoId, ESTADO_SOLICITUD_APROBADA, finPeriodo);
    }

    /**
     * Si alguna papeleta de {@code justificantes} cubre la fecha, devuelve el día
     * justificado (TELETRABAJO/PERMISO) que reemplaza a la FALTA; si no, vacío.
     */
    public Optional<AsistenciaDiaDto> justificar(LocalDate fecha, List<SolicitudRrhh> justificantes) {
        if (fecha == null || justificantes == null) {
            return Optional.empty();
        }
        for (SolicitudRrhh s : justificantes) {
            if (cubreFecha(s, fecha)) {
                return Optional.of(construir(fecha, s));
            }
        }
        return Optional.empty();
    }

    /** El día cae dentro del rango [fechaInicio, fechaFin] del permiso (o el único día). */
    private boolean cubreFecha(SolicitudRrhh s, LocalDate fecha) {
        LocalDate ini = s.getFechaInicio();
        LocalDate fin = s.getFechaFin();
        if (ini == null) {
            return false;
        }
        if (fin == null) {
            return fecha.isEqual(ini);
        }
        return !fecha.isBefore(ini) && !fecha.isAfter(fin);
    }

    private AsistenciaDiaDto construir(LocalDate fecha, SolicitudRrhh s) {
        TipoSolicitudRrhh tipo = s.getTipoSolicitud();
        boolean esTeletrabajo = tipo != null && CODIGO_TELETRABAJO.equals(tipo.getCodigo());

        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setDia(fecha);
        dia.setTipoDia(esTeletrabajo ? TIPO_DIA_TELETRABAJO : TIPO_DIA_PERMISO);
        dia.setMinutosTardanza(0);
        dia.setObservacion(observacion(s, tipo, esTeletrabajo));
        dia.setOrigen("PAPELETA");
        return dia;
    }

    private String observacion(SolicitudRrhh s, TipoSolicitudRrhh tipo, boolean esTeletrabajo) {
        String nombreTipo = tipo != null && tipo.getNombre() != null
                ? tipo.getNombre()
                : (esTeletrabajo ? "Teletrabajo" : "Permiso con goce");
        StringBuilder sb = new StringBuilder("Justificado por papeleta aprobada: ")
                .append(nombreTipo);
        if (s.getId() != null) {
            sb.append(" N°").append(s.getId());
        }
        if (s.getFechaAprobacion() != null) {
            sb.append(" (aprobada ").append(s.getFechaAprobacion().toLocalDate().format(FECHA)).append(")");
        }
        if (esTeletrabajo) {
            sb.append(" — Ley N° 31572");
        }
        return sb.toString();
    }
}
