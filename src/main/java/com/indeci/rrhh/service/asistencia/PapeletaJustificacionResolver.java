package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoLicencia;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoLicenciaRepository;
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

    /** ESTADO_SOLICITUD_ID = 9 → APROBADA (verificado en INDECI_ESTADO_SOLICITUD: 9=Aprobado por RRHH). */
    private static final long ESTADO_SOLICITUD_APROBADA = 9L;
    private static final String CODIGO_TELETRABAJO = "TELETRABAJO";
    /** Código del tipo "Solicitud de Vacaciones" (INDECI_TIPO_SOLICITUD_RRHH). */
    private static final String CODIGO_VACACIONES = "012";
    /** Código del tipo "Licencia" (INDECI_TIPO_SOLICITUD_RRHH) — con/sin goce se distingue por TipoLicencia.esSinGoce. */
    private static final String CODIGO_LICENCIA = "011";
    /** Código del tipo "Permiso de Justificación de Omisión de Registro de Asistencia". */
    private static final String CODIGO_JUSTIF_OMISION = "004";
    static final String TIPO_DIA_TELETRABAJO = "TELETRABAJO";
    static final String TIPO_DIA_PERMISO = "PERMISO";
    static final String TIPO_DIA_VACACIONES = "VACACIONES";
    static final String TIPO_DIA_LICENCIA = "LICENCIA";
    static final String TIPO_DIA_ASISTENCIA_JUSTIFICADA = "ASISTENCIA_JUSTIFICADA";
    static final String TIPO_DIA_LABORAL = "LABORAL";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Códigos que este resolver reconoce y reclasifica por su PROPIA regla específica
     * (Vacaciones, Omisión), sin depender del flag genérico JUSTIFICA_ASISTENCIA — ese flag
     * nunca está en 1 para ellos (V012_36: "tienen su propio tipo de día"). Deben incluirse
     * siempre en la consulta o {@link #justificarVacacionSobreMarcacion} y
     * {@link #justificarOmision} nunca reciben su propia papeleta en la lista.
     */
    private static final List<String> CODIGOS_SIEMPRE_INCLUIDOS =
            List.of(CODIGO_VACACIONES, CODIGO_JUSTIF_OMISION);

    private final SolicitudRrhhRepository solicitudRrhhRepository;
    private final TipoLicenciaRepository tipoLicenciaRepository;

    /**
     * Papeletas aprobadas y justificantes del empleado que podrían cubrir el período
     * (una sola consulta por empleado). Devuelve lista vacía si no hay datos válidos.
     */
    public List<SolicitudRrhh> cargarJustificantes(Long empleadoId, LocalDate finPeriodo) {
        if (empleadoId == null || finPeriodo == null) {
            return List.of();
        }
        return solicitudRrhhRepository.findJustificantesAsistencia(
                empleadoId, ESTADO_SOLICITUD_APROBADA, finPeriodo, CODIGOS_SIEMPRE_INCLUIDOS);
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

    /**
     * ¿La fecha (una omisión de marcación) está cubierta por una papeleta 004 APROBADA
     * ("Justificación de Omisión de Registro de Asistencia")? Se usa para convertir un día
     * {@code OMISION_MARCACION} en {@code ASISTENCIA_JUSTIFICADA} (tiempo completo, no descuenta).
     */
    public boolean omisionJustificada(LocalDate fecha, List<SolicitudRrhh> justificantes) {
        if (fecha == null || justificantes == null) {
            return false;
        }
        for (SolicitudRrhh s : justificantes) {
            TipoSolicitudRrhh tipo = s.getTipoSolicitud();
            if (tipo != null && CODIGO_JUSTIF_OMISION.equals(tipo.getCodigo()) && cubreFecha(s, fecha)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Si una papeleta 004 aprobada cubre la fecha, devuelve el día como
     * {@code ASISTENCIA_JUSTIFICADA} (reemplaza a la omisión); si no, vacío.
     */
    public Optional<AsistenciaDiaDto> justificarOmision(
            LocalDate fecha, List<SolicitudRrhh> justificantes) {
        if (!omisionJustificada(fecha, justificantes)) {
            return Optional.empty();
        }
        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setDia(fecha);
        dia.setTipoDia(TIPO_DIA_ASISTENCIA_JUSTIFICADA);
        dia.setMinutosTardanza(0);
        dia.setMinutosSalidaAnticipada(0);
        dia.setObservacion("Omisión de marcación justificada por papeleta 004 aprobada.");
        dia.setOrigen("PAPELETA");
        return Optional.of(dia);
    }

    /**
     * Si alguna papeleta de {@code justificantes} cubre la fecha, limpia la salida anticipada:
     * el día vuelve a {@code LABORAL} ("Presente") con los minutos en cero — no se reclasifica
     * a {@code PERMISO} (esa condición es para permisos de día completo, no para cubrir una
     * ausencia parcial ya marcada). Decisión RR.HH. 2026-08-07.
     */
    public Optional<AsistenciaDiaDto> justificarSalidaAnticipada(
            LocalDate fecha, List<SolicitudRrhh> justificantes) {
        if (fecha == null || justificantes == null) {
            return Optional.empty();
        }
        for (SolicitudRrhh s : justificantes) {
            if (cubreFecha(s, fecha)) {
                AsistenciaDiaDto dia = new AsistenciaDiaDto();
                dia.setDia(fecha);
                dia.setTipoDia(TIPO_DIA_LABORAL);
                dia.setMinutosTardanza(0);
                dia.setMinutosSalidaAnticipada(0);
                dia.setObservacion(observacionSalidaAnticipada(s));
                dia.setOrigen("PAPELETA");
                return Optional.of(dia);
            }
        }
        return Optional.empty();
    }

    private String observacionSalidaAnticipada(SolicitudRrhh s) {
        TipoSolicitudRrhh tipo = s.getTipoSolicitud();
        String nombreTipo = tipo != null && tipo.getNombre() != null ? tipo.getNombre() : "Permiso";
        StringBuilder sb = new StringBuilder("Salida anticipada justificada por papeleta aprobada: ")
                .append(nombreTipo);
        if (s.getId() != null) {
            sb.append(" N°").append(s.getId());
        }
        if (s.getFechaAprobacion() != null) {
            sb.append(" (aprobada ").append(s.getFechaAprobacion().toLocalDate().format(FECHA)).append(")");
        }
        return sb.append('.').toString();
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
        boolean esVacaciones = tipo != null && CODIGO_VACACIONES.equals(tipo.getCodigo());
        boolean esLicencia = tipo != null && CODIGO_LICENCIA.equals(tipo.getCodigo());

        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setDia(fecha);
        dia.setTipoDia(tipoDiaPara(esTeletrabajo, esVacaciones, esLicencia));
        dia.setMinutosTardanza(0);
        dia.setMinutosSalidaAnticipada(0);
        dia.setObservacion(observacion(s, tipo, esTeletrabajo, esLicencia));
        dia.setOrigen("PAPELETA");
        return dia;
    }

    private String tipoDiaPara(boolean esTeletrabajo, boolean esVacaciones, boolean esLicencia) {
        if (esTeletrabajo) return TIPO_DIA_TELETRABAJO;
        if (esVacaciones) return TIPO_DIA_VACACIONES;
        if (esLicencia) return TIPO_DIA_LICENCIA;
        return TIPO_DIA_PERMISO;
    }

    /**
     * Decisión RR.HH.: la LICENCIA (código "011") no es un único tipo — hay que distinguir
     * con/sin goce (TipoLicencia.esSinGoce), porque legalmente son categorías distintas (sin
     * goce no es remunerada y es no computable para récord/tiempo de servicio en otros
     * módulos). Ambas comparten {@code TIPO_DIA = LICENCIA} (única categoría válida del CHECK
     * de BD para licencias), pero la observación deja explícito cuál de las dos es.
     */
    private boolean esLicenciaSinGoce(SolicitudRrhh s) {
        if (s.getTipoLicenciaId() == null) {
            return false;
        }
        return tipoLicenciaRepository.findById(s.getTipoLicenciaId())
                .map(TipoLicencia::getEsSinGoce)
                .map(v -> Integer.valueOf(1).equals(v))
                .orElse(false);
    }

    /**
     * Decisión RR.HH.: la papeleta de VACACIONES aprobada MANDA sobre una marcación física real
     * (LABORAL/TARDANZA) — legal y remunerativamente el trabajador está de vacaciones, así que
     * el día se reclasifica a VACACIONES aunque el marcador haya registrado su ingreso. No se
     * hace en silencio: la observación deja explícito que se ignoró una marcación real, para que
     * RR.HH. tenga rastro si necesita investigar el caso. Solo aplica a Vacaciones — Teletrabajo/
     * Permiso no tienen definida esta regla de "manda sobre lo ya marcado".
     */
    public Optional<AsistenciaDiaDto> justificarVacacionSobreMarcacion(
            LocalDate fecha, List<SolicitudRrhh> justificantes) {
        if (fecha == null || justificantes == null) {
            return Optional.empty();
        }
        for (SolicitudRrhh s : justificantes) {
            TipoSolicitudRrhh tipo = s.getTipoSolicitud();
            if (tipo != null && CODIGO_VACACIONES.equals(tipo.getCodigo()) && cubreFecha(s, fecha)) {
                AsistenciaDiaDto dia = new AsistenciaDiaDto();
                dia.setDia(fecha);
                dia.setTipoDia(TIPO_DIA_VACACIONES);
                dia.setMinutosTardanza(0);
                dia.setMinutosSalidaAnticipada(0);
                dia.setObservacion(observacionSobrescritura(s, tipo));
                dia.setOrigen("PAPELETA");
                return Optional.of(dia);
            }
        }
        return Optional.empty();
    }

    private String observacionSobrescritura(SolicitudRrhh s, TipoSolicitudRrhh tipo) {
        String nombreTipo = tipo != null && tipo.getNombre() != null
                ? tipo.getNombre()
                : "Solicitud de Vacaciones";
        StringBuilder sb = new StringBuilder("Se ignoró marcación física por papeleta aprobada: ")
                .append(nombreTipo);
        if (s.getId() != null) {
            sb.append(" N°").append(s.getId());
        }
        if (s.getFechaAprobacion() != null) {
            sb.append(" (aprobada ").append(s.getFechaAprobacion().toLocalDate().format(FECHA)).append(")");
        }
        return sb.append('.').toString();
    }

    private String observacion(
            SolicitudRrhh s, TipoSolicitudRrhh tipo, boolean esTeletrabajo, boolean esLicencia) {
        String nombreTipo;
        if (esLicencia) {
            nombreTipo = esLicenciaSinGoce(s) ? "LICENCIA SIN GOCE" : "LICENCIA CON GOCE";
        } else {
            nombreTipo = tipo != null && tipo.getNombre() != null
                    ? tipo.getNombre()
                    : (esTeletrabajo ? "Teletrabajo" : "Permiso con goce");
        }
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
