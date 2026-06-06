package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EstimacionNetoDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.service.CalculoNetoHelper.ConceptoAplicado;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spec 013 / C1 — Estima el neto de un empleado si se le agregara un descuento
 * nuevo, SIN grabar nada en BD. Alimenta el preview del modal "Asignar
 * Descuento / Ajuste Manual".
 *
 * <p>Clase y service NUEVOS. Es de SOLO LECTURA: deliberadamente sin
 * {@code @Transactional} y sin repositorios de escritura. NO modifica ni
 * reemplaza a {@code GeneradorPlanillaService}; reutiliza {@link CalculoNetoHelper}
 * para la aritmética del neto.
 *
 * <p>Alcance de la estimación: el neto sale exclusivamente de
 * {@code INDECI_EMPLEADO_CONCEPTO} (lo mismo que el motor toma como ingresos
 * remunerativos y descuentos manuales). NO recalcula ONP/AFP, retención 5ta ni
 * ESSALUD — esos los computa el motor en la generación real. El preview es, por
 * tanto, una aproximación conservadora suficiente para el control SERVIR-07.
 */
@Service
@RequiredArgsConstructor
public class EstimacionNetoService {

    /**
     * CODIGO_MEF que el motor calcula automáticamente y, por tanto, IGNORA como
     * EmpleadoConcepto manual. Espeja {@code GeneradorPlanillaService.MEF_AUTOCALCULADOS}
     * (constante privada allí; aquí se replica para no modificar esa clase).
     */
    private static final Set<String> MEF_AUTOCALCULADOS = Set.of(
            "00302", "00502",                   // asignación familiar 728 / CAS
            "05001", "05002", "05003", "05004", // aporte ONP / AFP + comisión + prima
            "05101",                            // retención 5ta categoría
            "06001", "06002", "05309",          // ESSALUD sin/con EPS + copago EPS
            "05401", "05402");                  // descuento tardanza / falta

    /**
     * Conceptos de remuneración base (mejora 2026-06-03): la base es
     * {@code EmpleadoPlanilla.sueldoBasico} (Configuración de planilla), no un
     * concepto manual. Se excluyen aquí para no doble-contar la base.
     */
    private static final Set<String> MEF_BASE_REMUNERATIVA = Set.of(
            "00101", "00102", "00301", "00501",
            "L001", "L002", "L003", "L004");

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final EmpleadoConceptoRepository empleadoConceptoRepository;
    private final ConceptoPlanillaRepository conceptoPlanillaRepository;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final CalculoNetoHelper calculoNetoHelper;

    /**
     * Estima el neto del empleado agregando un descuento propuesto.
     *
     * @param empleadoId     empleado a evaluar.
     * @param conceptoId     concepto de planilla del descuento propuesto.
     * @param montoDescuento monto mensual del descuento propuesto ({@code null} → 0).
     * @return {@link EstimacionNetoDto} con neto actual, neto estimado, diferencia
     *         y la evaluación de la REGLA SERVIR-07.
     */
    public EstimacionNetoDto estimarNeto(
            Long empleadoId, Long conceptoId, BigDecimal montoDescuento) {

        // Sueldo básico — base para conceptos definidos por porcentaje.
        BigDecimal sueldoBasico = empleadoPlanillaRepository
                .findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(p -> toBigDecimal(p.getSueldoBasico()))
                .orElse(BigDecimal.ZERO);

        // Conceptos activos del empleado (solo lectura).
        List<EmpleadoConcepto> activos = empleadoConceptoRepository
                .findByEmpleadoIdAndActivo(empleadoId, 1);

        // Base remunerativa = sueldoBasico (mejora 2026-06-03): igual que el motor,
        // la base viene de Configuración de planilla, no de un concepto manual.
        BigDecimal remuneracion = sueldoBasico;
        List<ConceptoAplicado> conceptosVariables = new ArrayList<>();

        for (EmpleadoConcepto ec : activos) {
            ConceptoPlanilla cp = conceptoPlanillaRepository
                    .findById(ec.getConceptoPlanillaId())
                    .orElse(null);
            if (cp == null) {
                continue;
            }
            // El motor ignora los autocalculados y la remuneración base — la
            // estimación también (la base ya está incluida vía sueldoBasico).
            if (cp.getCodigoMef() != null
                    && (MEF_AUTOCALCULADOS.contains(cp.getCodigoMef())
                            || MEF_BASE_REMUNERATIVA.contains(cp.getCodigoMef()))) {
                continue;
            }
            BigDecimal monto = resolverMonto(ec, sueldoBasico);
            String tipo = resolverTipo(cp);
            if ("REMUNERATIVO".equals(tipo)) {
                remuneracion = remuneracion.add(monto);
            } else {
                conceptosVariables.add(new ConceptoAplicado(tipo, monto));
            }
        }

        BigDecimal netoActual = calculoNetoHelper.calcularNeto(conceptosVariables, remuneracion);

        // Descuento propuesto — concepto temporal, sin INSERT.
        ConceptoPlanilla conceptoNuevo = conceptoPlanillaRepository.findById(conceptoId)
                .orElseThrow(() -> new NegocioException(
                        "Concepto no existe: id=" + conceptoId));
        BigDecimal montoNuevo = montoDescuento == null ? BigDecimal.ZERO : montoDescuento;

        List<ConceptoAplicado> conNuevo = new ArrayList<>(conceptosVariables);
        conNuevo.add(new ConceptoAplicado(resolverTipo(conceptoNuevo), montoNuevo));

        BigDecimal netoEstimado = calculoNetoHelper.calcularNeto(conNuevo, remuneracion);
        boolean cumple = calculoNetoHelper.validarRegla50(netoEstimado, remuneracion);

        EstimacionNetoDto dto = new EstimacionNetoDto();
        dto.setNetoActual(netoActual);
        dto.setNetoEstimado(netoEstimado);
        dto.setDiferencia(netoEstimado.subtract(netoActual).setScale(2, RoundingMode.HALF_UP));
        dto.setCumpleRegla50(cumple);
        if (!cumple) {
            dto.setMensajeAlerta(
                    "El neto estimado (S/ " + netoEstimado.toPlainString() + ") cae por debajo "
                            + "del 50% de la remuneración (REGLA SERVIR-07 / SPEC §5.4). "
                            + "No se puede registrar este descuento.");
        }
        return dto;
    }

    /** Monto del EmpleadoConcepto: prioriza MONTO; si no, PORCENTAJE × sueldo básico. */
    private BigDecimal resolverMonto(EmpleadoConcepto ec, BigDecimal sueldoBasico) {
        if (ec.getMonto() != null) {
            return BigDecimal.valueOf(ec.getMonto());
        }
        if (ec.getPorcentaje() != null && sueldoBasico.signum() > 0) {
            return sueldoBasico
                    .multiply(BigDecimal.valueOf(ec.getPorcentaje()))
                    .divide(CIEN, 6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Resuelve el tipo del concepto. Prioriza {@code TIPO_CONCEPTO}; si está
     * vacío hace fallback al {@code TIPO} legacy. Espeja la lógica de
     * {@code GeneradorPlanillaService.resolverTipoConcepto}.
     */
    private String resolverTipo(ConceptoPlanilla concepto) {
        if (concepto.getTipoConcepto() != null && !concepto.getTipoConcepto().isBlank()) {
            return concepto.getTipoConcepto().toUpperCase();
        }
        String legacy = concepto.getTipo();
        if (legacy == null) {
            return "REMUNERATIVO";
        }
        return switch (legacy.toUpperCase()) {
            case "INGRESO"   -> "REMUNERATIVO";
            case "DESCUENTO" -> "DESCUENTO";
            case "APORTE"    -> "APORTE_TRABAJADOR";
            default          -> "REMUNERATIVO";
        };
    }

    private BigDecimal toBigDecimal(Double valor) {
        return valor == null ? BigDecimal.ZERO : BigDecimal.valueOf(valor);
    }
}
