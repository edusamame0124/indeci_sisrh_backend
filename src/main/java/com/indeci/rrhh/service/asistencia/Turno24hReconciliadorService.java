package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.EmpleadoTurno24h;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoTurno24hRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reconciliación de guardias COEN de 24h (08:30→08:30 del día siguiente) — directiva
 * RIS INDECI 2026-08-09. {@link AsistenciaEventosReader} agrupa marcas por día calendario
 * y, con solo 1 marca por día, clasifica ambos extremos de la guardia ("Falta: solo
 * ingreso") como FALTA — el ingreso real del día N y el cierre real (que en realidad es
 * la salida) del día N+1, porque ambas caen antes de las 14:00 y el corte de mediodía no
 * distingue nada para un turno que empieza y termina a la misma hora.
 *
 * <p>Este servicio corre DESPUÉS de {@code recalcularTardanzaDesdeMarcas} (que ignora los
 * días en FALTA, así que no hay interferencia) y detecta el patrón de días consecutivos
 * huérfanos para empleados con turno 24h vigente, los reclasifica (Día N=LABORAL absorbe
 * la guardia, Día N+1=DESCANSO post-guardia — RIS "sin alterar el número de horas
 * trabajadas") y ajusta los agregados de la cabecera para que el descuento se recalcule
 * bien en {@code refrescarDescuentos}. Idempotente: una vez reclasificado, el día deja de
 * calzar el patrón (ya no es FALTA), así que una segunda pasada no lo vuelve a tocar.
 *
 * <p>No toca la hora de referencia del empleado (su régimen laboral real para planilla
 * sigue siendo 1057/276/30057/SERVIR) — la hora de inicio/fin de guardia (08:30) se lee
 * del régimen "COEN" (antes "Otros", código {@value #CODIGO_REGIMEN_COEN}), que es una
 * máscara de jornada compartida, no un régimen de planilla nuevo (LEY-01: catálogo MEF
 * intacto).
 */
@Service
@RequiredArgsConstructor
public class Turno24hReconciliadorService {

    private static final Logger log = LoggerFactory.getLogger(Turno24hReconciliadorService.class);

    /** Código del régimen laboral que representa el turno continuo COEN (antes "Otros"). */
    static final String CODIGO_REGIMEN_COEN = "9999";

    /**
     * Ventana de tolerancia (minutos) para emparejar el cierre de guardia con el inicio
     * esperado del turno. Edge case guard explícito (directiva RR.HH. 2026-08-09): si la
     * marca del día N+1 no cae dentro de esta ventana, NO se reconcilia — se deja tal cual
     * (revisión manual), nunca se convierte a ciegas una guardia abandonada en LABORAL.
     */
    static final int VENTANA_CIERRE_MIN = 90;

    private final EmpleadoTurno24hRepository turno24hRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final JornadaRegimenRepository jornadaRegimenRepository;
    private final AsistenciaDetalleRepository detalleRepository;

    /**
     * Reconcilia los días de guardia COEN de la cabecera dada. No hace nada si el empleado
     * no tiene turno 24h activo (early-exit barato para el 99% de los casos), o si el
     * régimen COEN no tiene jornada configurada.
     *
     * @return cantidad de días reclasificados (0, o 2 por cada guardia emparejada).
     */
    public int reconciliar(AsistenciaCabecera cabecera) {
        List<EmpleadoTurno24h> turnos = turno24hRepository
                .findByEmpleadoIdAndActivoOrderByFechaInicioDesc(cabecera.getEmpleadoId(), 1);
        if (turnos.isEmpty()) {
            return 0;
        }

        JornadaRegimen jornadaCoen = regimenCoen();
        if (jornadaCoen == null || esVacio(jornadaCoen.getHoraIngreso())) {
            log.warn("Turno 24h: régimen COEN sin jornada configurada; empleado {} no se reconcilia.",
                    cabecera.getEmpleadoId());
            return 0;
        }

        List<AsistenciaDetalle> detalles = detalleRepository.findByCabeceraIdOrderByDia(cabecera.getId());
        Map<LocalDate, AsistenciaDetalle> porDia = detalles.stream()
                .collect(Collectors.toMap(AsistenciaDetalle::getDia, d -> d, (a, b) -> a));

        List<AsistenciaDetalle> modificados = new java.util.ArrayList<>();
        for (AsistenciaDetalle diaN : detalles) {
            if (diaN.getDia() == null) {
                continue;
            }
            AsistenciaDetalle diaN1 = porDia.get(diaN.getDia().plusDays(1));
            if (diaN1 == null) {
                continue;
            }
            if (!esCandidatoOrfano(diaN) || !esCandidatoOrfano(diaN1)) {
                continue;
            }
            if (!turnoCubre(turnos, diaN.getDia())) {
                continue;
            }
            if (!marcaEnVentana(diaN.getMarcaEntrada(), jornadaCoen.getHoraIngreso())
                    || !marcaEnVentana(diaN1.getMarcaEntrada(), jornadaCoen.getHoraIngreso())) {
                // Guardia de oro: no calza limpio (abandono de guardia, marca ausente
                // o muy fuera de horario) -> no se toca, queda para revisión manual.
                continue;
            }

            aplicarReconciliacion(diaN, diaN1, jornadaCoen);
            modificados.add(diaN);
            modificados.add(diaN1);
        }

        if (modificados.isEmpty()) {
            return 0;
        }
        detalleRepository.saveAll(modificados);
        ajustarAgregadosCabecera(cabecera, modificados.size() / 2);
        return modificados.size();
    }

    private void aplicarReconciliacion(AsistenciaDetalle diaN, AsistenciaDetalle diaN1, JornadaRegimen jornadaCoen) {
        String obs = "Reconciliado automáticamente — turno COEN 24h "
                + diaN.getMarcaEntrada() + " " + diaN.getDia() + " -> "
                + diaN1.getMarcaEntrada() + " " + diaN1.getDia()
                + " (RIS INDECI, turno continuo, sin alterar horas trabajadas).";

        Integer tardanza = TardanzaCalculator.calcular(diaN.getMarcaEntrada(), null, jornadaCoen);
        diaN.setTipoDia("LABORAL");
        diaN.setMinutosTardanza(tardanza != null ? tardanza : 0);
        diaN.setObservacion(obs);
        diaN.setOrigen("TURNO_24H");

        diaN1.setTipoDia("DESCANSO");
        diaN1.setMinutosTardanza(0);
        diaN1.setObservacion(obs);
        diaN1.setOrigen("TURNO_24H");
    }

    /** Cada guardia reconciliada saca 2 días de FALTA (día N y N+1) y suma 1 día laborado (día N). */
    private void ajustarAgregadosCabecera(AsistenciaCabecera cabecera, int guardiasReconciliadas) {
        int diasFaltaActual = cabecera.getDiasFalta() != null ? cabecera.getDiasFalta() : 0;
        int diasLaboradosActual = cabecera.getDiasLaborados() != null ? cabecera.getDiasLaborados() : 0;
        cabecera.setDiasFalta(Math.max(0, diasFaltaActual - (guardiasReconciliadas * 2)));
        cabecera.setDiasLaborados(diasLaboradosActual + guardiasReconciliadas);
    }

    /** FALTA con solo Marca1 (Marca de ingreso) y ninguna otra marca — el patrón huérfano de AsistenciaEventosReader. */
    private boolean esCandidatoOrfano(AsistenciaDetalle d) {
        return "FALTA".equals(d.getTipoDia())
                && !esVacio(d.getMarcaEntrada())
                && esVacio(d.getMarcaSalida())
                && esVacio(d.getMarca3())
                && esVacio(d.getMarca4());
    }

    private boolean turnoCubre(List<EmpleadoTurno24h> turnos, LocalDate fecha) {
        return turnos.stream().anyMatch(t -> t.cubre(fecha));
    }

    private boolean marcaEnVentana(String marca, String referencia) {
        Integer m = toMinutos(marca);
        Integer r = toMinutos(referencia);
        if (m == null || r == null) {
            return false;
        }
        return Math.abs(m - r) <= VENTANA_CIERRE_MIN;
    }

    private Integer toMinutos(String hora) {
        if (hora == null || hora.isBlank()) {
            return null;
        }
        String[] p = hora.trim().split(":");
        if (p.length < 2) {
            return null;
        }
        try {
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private JornadaRegimen regimenCoen() {
        return regimenLaboralRepository.findByCodigo(CODIGO_REGIMEN_COEN)
                .map(RegimenLaboral::getId)
                .flatMap(jornadaRegimenRepository::findByRegimenLaboralId)
                .orElse(null);
    }

    private boolean esVacio(String s) {
        return s == null || s.isBlank();
    }
}
