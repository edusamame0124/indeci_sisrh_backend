package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.DescansoSemanal;
import com.indeci.rrhh.entity.Feriado;
import com.indeci.rrhh.repository.DescansoSemanalRepository;
import com.indeci.rrhh.repository.FeriadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calendario laboral (M04 · F3): decide qué días del período son laborables para
 * generar las FALTAS de los días sin marca (feriados/descansos/vínculo). Carga
 * feriados (por año) y descansos (global/por régimen) una sola vez por período.
 */
@Service
@RequiredArgsConstructor
public class CalendarioLaboralService {

    /** Descanso por defecto si la tabla no está sembrada: sábado(6) y domingo(7). */
    private static final Set<Integer> DESCANSO_DEFECTO = Set.of(6, 7);

    private final FeriadoRepository feriadoRepository;
    private final DescansoSemanalRepository descansoRepository;

    @Transactional(readOnly = true)
    public Calendario paraPeriodo(LocalDate inicio, LocalDate fin) {
        Set<Integer> anios = new HashSet<>();
        anios.add(inicio.getYear());
        anios.add(fin.getYear());

        Set<LocalDate> feriados = new HashSet<>();
        for (Feriado f : feriadoRepository.findByAnioInAndActivo(anios, 1)) {
            if (f.getFecha() != null) {
                feriados.add(f.getFecha());
            }
        }

        Set<Integer> descansoGlobal = new HashSet<>();
        Map<Long, Set<Integer>> descansoPorRegimen = new HashMap<>();
        for (DescansoSemanal d : descansoRepository.findByActivo(1)) {
            if (d.getDiaIso() == null) {
                continue;
            }
            if (d.getRegimenLaboralId() == null) {
                descansoGlobal.add(d.getDiaIso());
            } else {
                descansoPorRegimen
                        .computeIfAbsent(d.getRegimenLaboralId(), k -> new HashSet<>())
                        .add(d.getDiaIso());
            }
        }
        // Blindaje: si nadie configuró el descanso global, usar sábado+domingo
        // (evita marcar como falta los fines de semana por falta de seed).
        if (descansoGlobal.isEmpty()) {
            descansoGlobal.addAll(DESCANSO_DEFECTO);
        }
        return new Calendario(feriados, descansoGlobal, descansoPorRegimen);
    }

    /** Snapshot inmutable del calendario para un período; funciones puras y testeables. */
    public static final class Calendario {

        private final Set<LocalDate> feriados;
        private final Set<Integer> descansoGlobal;
        private final Map<Long, Set<Integer>> descansoPorRegimen;

        public Calendario(
                Set<LocalDate> feriados,
                Set<Integer> descansoGlobal,
                Map<Long, Set<Integer>> descansoPorRegimen) {
            this.feriados = feriados;
            this.descansoGlobal = descansoGlobal;
            this.descansoPorRegimen = descansoPorRegimen;
        }

        public boolean esFeriado(LocalDate fecha) {
            return feriados.contains(fecha);
        }

        public boolean esDescanso(LocalDate fecha, Long regimenLaboralId) {
            int iso = fecha.getDayOfWeek().getValue(); // 1=Lunes..7=Domingo
            if (descansoGlobal.contains(iso)) {
                return true;
            }
            Set<Integer> delRegimen = regimenLaboralId != null
                    ? descansoPorRegimen.get(regimenLaboralId)
                    : null;
            return delRegimen != null && delRegimen.contains(iso);
        }

        public boolean esLaborable(LocalDate fecha, Long regimenLaboralId) {
            return !esFeriado(fecha) && !esDescanso(fecha, regimenLaboralId);
        }

        /**
         * Días laborables del período (respetando vínculo vigente ingreso/cese) que
         * NO tienen marca — candidatos a FALTA. {@code presentes} = días con registro.
         */
        public List<LocalDate> diasFalta(
                LocalDate inicio,
                LocalDate fin,
                LocalDate ingreso,
                LocalDate cese,
                Long regimenLaboralId,
                Set<LocalDate> presentes) {

            List<LocalDate> faltas = new ArrayList<>();
            Set<LocalDate> yaPresentes = presentes != null ? presentes : Set.of();
            for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
                if (yaPresentes.contains(dia)) {
                    continue;
                }
                if (ingreso != null && dia.isBefore(ingreso)) {
                    continue;
                }
                if (cese != null && dia.isAfter(cese)) {
                    continue;
                }
                if (esLaborable(dia, regimenLaboralId)) {
                    faltas.add(dia);
                }
            }
            return faltas;
        }
    }
}
