package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ReporteEvolucionDto;
import com.indeci.rrhh.dto.ReporteEvolucionDto.ReporteEvolucionItemDto;
import com.indeci.rrhh.dto.ReporteRegimenDto;
import com.indeci.rrhh.dto.ReporteRegimenDto.ReporteRegimenItemDto;
import com.indeci.rrhh.dto.ReporteTopConceptosDto;
import com.indeci.rrhh.dto.ReporteTopConceptosDto.ReporteTopConceptoItemDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import lombok.RequiredArgsConstructor;

/**
 * F3.5 — Reportes Consolidados (tablero analítico).
 *
 * <p>Tres operaciones solo lectura sobre datos ya grabados:</p>
 * <ul>
 *   <li>{@link #evolucion(String, int)} — totales por período en una ventana
 *       móvil de {@code meses} meses terminando en {@code periodoBase}.</li>
 *   <li>{@link #distribucionRegimen(String)} — agrupa los movimientos del
 *       período por régimen laboral del empleado.</li>
 *   <li>{@link #topConceptos(String, int)} — top {@code limite} conceptos por
 *       monto total en {@code MovimientoPlanillaDetalle} del período.</li>
 * </ul>
 *
 * <p>Sin gráficos: devuelve solo datos numéricos. La UI los pinta con
 * tabla + barras CSS proporcionales.</p>
 */
@Service
@RequiredArgsConstructor
public class ReporteConsolidadoService {

    /** Tipo MEF que debe sumar en "aporte empleador" (ESSALUD, etc.). */
    private static final String TIPO_APORTE_EMPLEADOR = "APORTE_EMPLEADOR";

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository detalleRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final ConceptoPlanillaRepository conceptoRepository;

    // ====================================================================
    // TAB 1 — EVOLUCIÓN MULTI-PERÍODO
    // ====================================================================

    public ReporteEvolucionDto evolucion(String periodoBase, int meses) {
        validarPeriodo(periodoBase);
        if (meses < 1 || meses > 24) {
            throw new NegocioException("La ventana debe ser entre 1 y 24 meses.");
        }

        List<String> periodos = construirRangoPeriodos(periodoBase, meses);
        List<ReporteEvolucionItemDto> items = new ArrayList<>(periodos.size());

        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal netoAnterior = null;

        for (String p : periodos) {
            List<MovimientoPlanilla> movs =
                    movimientoRepository.findByPeriodoAndActivo(p, 1);

            BigDecimal ingresos = BigDecimal.ZERO;
            BigDecimal descuentos = BigDecimal.ZERO;
            BigDecimal neto = BigDecimal.ZERO;
            int conteoNetoNoVa = 0;
            for (MovimientoPlanilla m : movs) {
                ingresos = ingresos.add(toBd(m.getTotalIngresos()));
                descuentos = descuentos.add(toBd(m.getTotalDescuentos()));
                neto = neto.add(toBd(m.getNetoPagar()));
                if ("NETO_NO_VA".equalsIgnoreCase(m.getEstadoNeto())) conteoNetoNoVa++;
            }

            BigDecimal aporteEmp = calcularAporteEmpleadorDelPeriodo(movs);

            BigDecimal delta = null;
            if (netoAnterior != null && netoAnterior.signum() != 0) {
                delta = neto.subtract(netoAnterior)
                        .divide(netoAnterior, 4, RoundingMode.HALF_UP)
                        .multiply(CIEN)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            items.add(new ReporteEvolucionItemDto(
                    p, movs.size(),
                    redondear(ingresos),
                    redondear(descuentos),
                    redondear(neto),
                    redondear(aporteEmp),
                    conteoNetoNoVa,
                    delta));

            acumulado = acumulado.add(neto);
            netoAnterior = neto;
        }

        BigDecimal promedio = items.isEmpty() ? BigDecimal.ZERO :
                acumulado.divide(BigDecimal.valueOf(items.size()),
                        2, RoundingMode.HALF_UP);

        BigDecimal variacionRango = null;
        if (items.size() >= 2) {
            BigDecimal primero = items.get(0).totalNeto();
            BigDecimal ultimo = items.get(items.size() - 1).totalNeto();
            if (primero.signum() != 0) {
                variacionRango = ultimo.subtract(primero)
                        .divide(primero, 4, RoundingMode.HALF_UP)
                        .multiply(CIEN)
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        return new ReporteEvolucionDto(
                periodoBase, meses, redondear(acumulado),
                promedio, variacionRango, items);
    }

    // ====================================================================
    // TAB 2 — DISTRIBUCIÓN POR RÉGIMEN
    // ====================================================================

    public ReporteRegimenDto distribucionRegimen(String periodo) {
        validarPeriodo(periodo);

        // Map empleadoId → régimen (código + nombre).
        Map<Long, RegimenLaboral> regimenPorEmpId = new HashMap<>();
        Map<Long, RegimenLaboral> regimenPorId = new HashMap<>();
        for (RegimenLaboral rl : regimenLaboralRepository.findAll()) {
            regimenPorId.put(rl.getId(), rl);
        }
        for (EmpleadoPlanilla pl : planillaRepository.findByActivo(1)) {
            if (pl.getRegimenLaboralId() != null) {
                RegimenLaboral rl = regimenPorId.get(pl.getRegimenLaboralId());
                if (rl != null) regimenPorEmpId.put(pl.getEmpleadoId(), rl);
            }
        }

        // Acumular por régimen.
        Map<String, AcumuladorRegimen> acc = new HashMap<>();
        for (MovimientoPlanilla m : movimientoRepository.findByPeriodoAndActivo(periodo, 1)) {
            RegimenLaboral rl = regimenPorEmpId.get(m.getEmpleadoId());
            String key = rl != null ? rl.getCodigo() : "—";
            AcumuladorRegimen a = acc.computeIfAbsent(key, k -> {
                AcumuladorRegimen nuevo = new AcumuladorRegimen();
                nuevo.codigo = key;
                nuevo.nombre = rl != null ? rl.getNombre() : "Sin régimen";
                return nuevo;
            });
            a.empleados++;
            a.ingresos = a.ingresos.add(toBd(m.getTotalIngresos()));
            a.descuentos = a.descuentos.add(toBd(m.getTotalDescuentos()));
            a.neto = a.neto.add(toBd(m.getNetoPagar()));
        }

        BigDecimal totalNeto = BigDecimal.ZERO;
        int totalEmpleados = 0;
        for (AcumuladorRegimen a : acc.values()) {
            totalNeto = totalNeto.add(a.neto);
            totalEmpleados += a.empleados;
        }

        List<ReporteRegimenItemDto> items = new ArrayList<>(acc.size());
        for (AcumuladorRegimen a : acc.values()) {
            BigDecimal promedio = a.empleados > 0
                    ? a.neto.divide(BigDecimal.valueOf(a.empleados),
                            2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal pct = totalNeto.signum() != 0
                    ? a.neto.divide(totalNeto, 4, RoundingMode.HALF_UP)
                            .multiply(CIEN).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(new ReporteRegimenItemDto(
                    a.codigo, a.nombre, a.empleados,
                    redondear(a.ingresos),
                    redondear(a.descuentos),
                    redondear(a.neto),
                    promedio, pct));
        }
        items.sort(Comparator.comparing(
                ReporteRegimenItemDto::totalNeto).reversed());

        return new ReporteRegimenDto(
                periodo, totalEmpleados, redondear(totalNeto), items);
    }

    // ====================================================================
    // TAB 3 — TOP CONCEPTOS
    // ====================================================================

    public ReporteTopConceptosDto topConceptos(String periodo, int limite) {
        validarPeriodo(periodo);
        if (limite < 1 || limite > 200) {
            throw new NegocioException("El límite debe ser entre 1 y 200.");
        }

        // Cargar conceptos en cache.
        Map<Long, ConceptoPlanilla> conceptoPorId = new HashMap<>();
        for (ConceptoPlanilla c : conceptoRepository.findByActivo(1)) {
            conceptoPorId.put(c.getId(), c);
        }

        Map<Long, AcumuladorConcepto> acc = new HashMap<>();
        BigDecimal totalIngresos = BigDecimal.ZERO;

        for (MovimientoPlanilla m : movimientoRepository.findByPeriodoAndActivo(periodo, 1)) {
            totalIngresos = totalIngresos.add(toBd(m.getTotalIngresos()));
            for (MovimientoPlanillaDetalle d :
                    detalleRepository.findByMovimientoPlanillaId(m.getId())) {
                if (d.getConceptoPlanillaId() == null) continue;
                AcumuladorConcepto a = acc.computeIfAbsent(
                        d.getConceptoPlanillaId(), id -> new AcumuladorConcepto());
                a.monto = a.monto.add(toBd(d.getMonto()));
                a.empleadosUnicos.add(m.getEmpleadoId());
            }
        }

        List<ReporteTopConceptoItemDto> items = new ArrayList<>(acc.size());
        for (Map.Entry<Long, AcumuladorConcepto> e : acc.entrySet()) {
            ConceptoPlanilla c = conceptoPorId.get(e.getKey());
            if (c == null) continue;
            BigDecimal pct = totalIngresos.signum() != 0
                    ? e.getValue().monto.divide(totalIngresos, 4, RoundingMode.HALF_UP)
                            .multiply(CIEN).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            items.add(new ReporteTopConceptoItemDto(
                    c.getId(),
                    c.getCodigoMef(),
                    c.getNombre(),
                    c.getTipoConcepto(),
                    e.getValue().empleadosUnicos.size(),
                    redondear(e.getValue().monto),
                    pct));
        }
        items.sort(Comparator.comparing(
                ReporteTopConceptoItemDto::montoTotal).reversed());

        if (items.size() > limite) {
            items = items.subList(0, limite);
        }
        return new ReporteTopConceptosDto(
                periodo, limite, redondear(totalIngresos), items);
    }

    // ====================================================================
    // HELPERS
    // ====================================================================

    private BigDecimal calcularAporteEmpleadorDelPeriodo(List<MovimientoPlanilla> movs) {
        if (movs.isEmpty()) return BigDecimal.ZERO;
        // Cache para reducir queries cuando el período tiene muchos empleados.
        Map<Long, ConceptoPlanilla> conceptoPorId = new HashMap<>();
        for (ConceptoPlanilla c : conceptoRepository.findByActivo(1)) {
            conceptoPorId.put(c.getId(), c);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (MovimientoPlanilla m : movs) {
            for (MovimientoPlanillaDetalle d :
                    detalleRepository.findByMovimientoPlanillaId(m.getId())) {
                if (d.getConceptoPlanillaId() == null) continue;
                ConceptoPlanilla c = conceptoPorId.get(d.getConceptoPlanillaId());
                if (c == null) continue;
                if (TIPO_APORTE_EMPLEADOR.equalsIgnoreCase(c.getTipoConcepto())) {
                    total = total.add(toBd(d.getMonto()));
                }
            }
        }
        return total;
    }

    /**
     * Construye los últimos {@code meses} períodos terminando en
     * {@code periodoBase}. Acepta formato {@code YYYY-MM} o {@code YYYYMM}
     * y devuelve los strings en el mismo formato del input.
     */
    private List<String> construirRangoPeriodos(String periodoBase, int meses) {
        boolean conGuion = periodoBase.contains("-");
        int anio;
        int mes;
        try {
            if (conGuion) {
                String[] partes = periodoBase.split("-");
                anio = Integer.parseInt(partes[0]);
                mes = Integer.parseInt(partes[1]);
            } else {
                anio = Integer.parseInt(periodoBase.substring(0, 4));
                mes = Integer.parseInt(periodoBase.substring(4));
            }
        } catch (Exception ex) {
            throw new NegocioException("Período inválido: " + periodoBase);
        }
        if (mes < 1 || mes > 12) {
            throw new NegocioException("Período inválido: " + periodoBase);
        }

        // Construir desde el más antiguo hacia el más reciente.
        List<String> ascendente = new ArrayList<>(meses);
        // Calcular total de meses desde año 0 para retroceder fácil.
        int totalMes = anio * 12 + (mes - 1);
        for (int i = meses - 1; i >= 0; i--) {
            int t = totalMes - i;
            int a = t / 12;
            int m = (t % 12) + 1;
            String s = conGuion
                    ? String.format("%04d-%02d", a, m)
                    : String.format("%04d%02d", a, m);
            ascendente.add(s);
        }
        return ascendente;
    }

    private void validarPeriodo(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            throw new NegocioException("Selecciona un período válido (YYYY-MM).");
        }
    }

    private BigDecimal toBd(Double v) {
        return v == null ? BigDecimal.ZERO : BigDecimal.valueOf(v);
    }

    private BigDecimal redondear(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    // Tipos auxiliares para acumular dentro del método.

    private static final class AcumuladorRegimen {
        String codigo;
        String nombre;
        int empleados;
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal descuentos = BigDecimal.ZERO;
        BigDecimal neto = BigDecimal.ZERO;
    }

    private static final class AcumuladorConcepto {
        BigDecimal monto = BigDecimal.ZERO;
        final Set<Long> empleadosUnicos = new HashSet<>();
    }
}
