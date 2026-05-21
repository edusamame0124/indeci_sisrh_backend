package com.indeci.rrhh.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MetaCertificacionDto;
import com.indeci.rrhh.dto.SemaforoMetaDto;
import com.indeci.rrhh.dto.SemaforoPresupuestalDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MetaPresupuestal;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MetaPresupuestalRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 012 / C1 · P-05 — Semáforo presupuestal por meta.
 *
 * <p>Compara, meta por meta, el monto CERTIFICADO (techo que carga Tesorería)
 * contra el monto COMPROMETIDO (suma de los netos de la planilla del período).
 * El comprometido se deriva en vivo; nunca se almacena.
 *
 * <p>Es información de control: no altera el flujo de aprobación del período
 * (los 3 gates de B7 siguen intactos).
 */
@Service
@RequiredArgsConstructor
public class MetaPresupuestalService {

    /** Etiqueta para empleados sin meta presupuestal asignada. */
    private static final String SIN_META = "(sin meta)";

    private final MetaPresupuestalRepository metaRepository;
    private final PeriodoPlanillaRepository periodoRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final EmpleadoPlanillaRepository planillaRepository;

    // ============================ SEMÁFORO ============================

    /** Semáforo presupuestal del período. Falla si el período no existe. */
    @Transactional(readOnly = true)
    public SemaforoPresupuestalDto semaforo(Long periodoId) {

        PeriodoPlanilla periodo = periodoRepository.findById(periodoId)
                .orElseThrow(() -> new NegocioException(
                        "Período no encontrado: " + periodoId));

        // 1) Comprometido por meta = suma de netos de la planilla del período.
        Map<String, Acumulado> porMeta = new TreeMap<>();
        for (MovimientoPlanilla mov :
                movimientoRepository.findByPeriodoAndActivo(periodo.getPeriodo(), 1)) {

            Optional<EmpleadoPlanilla> ep = planillaRepository
                    .findFirstByEmpleadoIdAndActivo(mov.getEmpleadoId(), 1);

            String meta = ep.map(EmpleadoPlanilla::getMeta)
                    .filter(m -> m != null && !m.isBlank())
                    .orElse(SIN_META);

            Acumulado acc = porMeta.computeIfAbsent(meta, k -> new Acumulado());
            acc.comprometido += nz(mov.getNetoPagar());
            acc.pea++;
            ep.ifPresent(p -> {
                if (acc.centroCosto == null) acc.centroCosto = p.getCentroCosto();
                if (acc.fuenteFinanc == null) acc.fuenteFinanc = p.getFuenteFinanciamiento();
            });
        }

        // 2) Certificado por meta (lo cargado por Tesorería para el período).
        for (MetaPresupuestal cert :
                metaRepository.findByPeriodoIdAndActivo(periodoId, 1)) {
            Acumulado acc = porMeta.computeIfAbsent(cert.getMeta(), k -> new Acumulado());
            acc.certificado = nz(cert.getMontoCertificado());
            if (cert.getCentroCosto() != null) acc.centroCosto = cert.getCentroCosto();
            if (cert.getFuenteFinanc() != null) acc.fuenteFinanc = cert.getFuenteFinanc();
        }

        // 3) Armar las filas del semáforo.
        List<SemaforoMetaDto> filas = new ArrayList<>();
        double totalCert = 0;
        double totalComp = 0;
        boolean algunaRoja = false;
        for (Map.Entry<String, Acumulado> e : porMeta.entrySet()) {
            Acumulado acc = e.getValue();
            boolean roja = acc.comprometido > acc.certificado;
            algunaRoja = algunaRoja || roja;
            totalCert += acc.certificado;
            totalComp += acc.comprometido;

            SemaforoMetaDto fila = new SemaforoMetaDto();
            fila.setMeta(e.getKey());
            fila.setCentroCosto(acc.centroCosto);
            fila.setFuenteFinanc(acc.fuenteFinanc);
            fila.setPea(acc.pea);
            fila.setMontoCertificado(acc.certificado);
            fila.setMontoComprometido(acc.comprometido);
            fila.setSaldo(acc.certificado - acc.comprometido);
            fila.setEstado(roja ? "ROJO" : "VERDE");
            filas.add(fila);
        }
        filas.sort(Comparator.comparing(SemaforoMetaDto::getMeta));

        SemaforoPresupuestalDto dto = new SemaforoPresupuestalDto();
        dto.setPeriodoId(periodoId);
        dto.setPeriodo(periodo.getPeriodo());
        dto.setMetas(filas);
        dto.setTotalCertificado(totalCert);
        dto.setTotalComprometido(totalComp);
        dto.setEstadoGlobal(algunaRoja ? "ROJO" : "VERDE");
        return dto;
    }

    // ============================ CERTIFICACIÓN ============================

    /**
     * Registra (upsert) los montos certificados por meta para el período.
     * Una entrada con meta en blanco se ignora.
     */
    @Transactional
    public void guardar(Long periodoId, List<MetaCertificacionDto> entradas) {

        periodoRepository.findById(periodoId)
                .orElseThrow(() -> new NegocioException(
                        "Período no encontrado: " + periodoId));

        if (entradas == null) return;

        for (MetaCertificacionDto in : entradas) {
            if (in.getMeta() == null || in.getMeta().isBlank()) continue;
            String meta = in.getMeta().trim();

            MetaPresupuestal row = metaRepository
                    .findByPeriodoIdAndMetaAndActivo(periodoId, meta, 1)
                    .orElseGet(() -> {
                        MetaPresupuestal nueva = new MetaPresupuestal();
                        nueva.setPeriodoId(periodoId);
                        nueva.setMeta(meta);
                        nueva.setActivo(1);
                        nueva.setCreatedAt(LocalDateTime.now());
                        return nueva;
                    });

            row.setCentroCosto(in.getCentroCosto());
            row.setFuenteFinanc(in.getFuenteFinanc());
            row.setMontoCertificado(nz(in.getMontoCertificado()));
            if (row.getId() != null) row.setUpdatedAt(LocalDateTime.now());
            metaRepository.save(row);
        }
    }

    // ============================ HELPERS ============================

    private static double nz(Double value) {
        return value == null ? 0d : value;
    }

    /** Acumulador en memoria por meta mientras se arma el semáforo. */
    private static final class Acumulado {
        private double comprometido;
        private double certificado;
        private int pea;
        private String centroCosto;
        private String fuenteFinanc;
    }
}
