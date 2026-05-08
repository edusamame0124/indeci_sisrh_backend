package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ResumenPlanillaDto;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeneradorPlanillaService {

    private final EmpleadoPlanillaRepository planillaRepository;



    private final ConceptoPlanillaRepository conceptoRepository;

    private final MovimientoPlanillaRepository movimientoRepository;

    private final MovimientoPlanillaDetalleRepository detalleRepository;

    private final PeriodoPlanillaRepository periodoRepository;
    private final EmpleadoConceptoRepository
    empleadoConceptoRepository;

    // ==========================================
    // GENERAR PLANILLA
    // ==========================================

    public void generar(Long empleadoId,
                        String periodo) {

        // ==========================================
        // VALIDAR PERIODO
        // ==========================================

        PeriodoPlanilla periodoPlanilla =
                periodoRepository
                        .findByPeriodoAndActivo(
                                periodo,
                                1)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Periodo no existe"));

        if ("CERRADO".equals(
                periodoPlanilla.getEstado())) {

        	throw new NegocioException(
        	        "El periodo "
        	                + periodo
        	                + " está cerrado");
        }

        // ==========================================
        // OBTENER CONFIG PLANILLA
        // ==========================================

        EmpleadoPlanilla planilla =
                planillaRepository
                        .findFirstByEmpleadoIdAndActivo(
                                empleadoId,
                                1)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Empleado sin configuración planilla"));

        // ==========================================

        // ==========================================
        // ELIMINAR ANTERIOR
        // ==========================================

        movimientoRepository
                .findByEmpleadoIdAndPeriodoAndActivo(
                        empleadoId,
                        periodo,
                        1)
                .ifPresent(mov -> {

                    detalleRepository
                            .deleteByMovimientoPlanillaId(
                                    mov.getId());

                    movimientoRepository.delete(mov);
                });

        // ==========================================
        // CREAR CABECERA
        // ==========================================

        MovimientoPlanilla movimiento =
                new MovimientoPlanilla();

        movimiento.setEmpleadoId(empleadoId);

        movimiento.setPeriodo(periodo);

        movimiento.setObservacion(
                "PLANILLA GENERADA");

        movimiento.setActivo(1);

        movimiento.setEstado("GENERADO");

        movimiento.setCreatedAt(
                LocalDateTime.now());

        movimiento.setTotalIngresos(0.0);

        movimiento.setTotalDescuentos(0.0);

        movimiento.setNetoPagar(0.0);

        movimientoRepository.save(movimiento);

        // ==========================================
        // GENERAR DETALLES
        // ==========================================

        double ingresos = 0;

        double descuentos = 0;

        // ==========================================
        // OBTENER CONCEPTOS EMPLEADO
        // ==========================================

        List<EmpleadoConcepto> conceptos =
                empleadoConceptoRepository
                        .findByEmpleadoIdAndActivo(
                                empleadoId,
                                1);

        // ==========================================
        // RECORRER CONCEPTOS
        // ==========================================

        for (EmpleadoConcepto ec : conceptos) {

            ConceptoPlanilla concepto =
                    conceptoRepository
                            .findById(
                                    ec.getConceptoPlanillaId())
                            .orElseThrow(() ->
                                    new NegocioException(
                                            "Concepto no existe"));

            double monto = 0;

        // ==========================================
        // CALCULAR MONTO
        // ==========================================

            if (ec.getMonto() != null) {

                monto = ec.getMonto();

            } else if (ec.getPorcentaje() != null) {

                monto =
                        planilla.getSueldoBasico()
                                * (ec.getPorcentaje() / 100);
            }

        // ==========================================
        // GENERAR DETALLE
        // ==========================================

            generarDetalle(
                    movimiento.getId(),
                    concepto.getId(),
                    monto,
                    concepto.getNombre());

        // ==========================================
        // ACUMULAR
        // ==========================================

            if ("INGRESO".equalsIgnoreCase(
                    concepto.getTipo())) {

                ingresos += monto;

            } else if ("DESCUENTO".equalsIgnoreCase(
                    concepto.getTipo())) {

                descuentos += monto;
            }
        }

        // ==========================================
        // ACTUALIZAR TOTALES
        // ==========================================

        movimiento.setTotalIngresos(
                ingresos);

        movimiento.setTotalDescuentos(
                descuentos);

        movimiento.setNetoPagar(
                ingresos - descuentos);

   

        movimientoRepository.save(movimiento);
    }

    // ==========================================
    // GENERAR DETALLE
    // ==========================================

    private double generarDetalle(
            Long movimientoId,
            Long conceptoId,
            Double monto,
            String observacion) {

        MovimientoPlanillaDetalle det =
                new MovimientoPlanillaDetalle();

        det.setMovimientoPlanillaId(
                movimientoId);

        det.setConceptoPlanillaId(
                conceptoId);

        det.setMonto(monto);

        det.setCantidad(1.0);

        det.setObservacion(observacion);

        det.setCreatedAt(
                LocalDateTime.now());

        detalleRepository.save(det);

        return monto;
    }

    // ==========================================
    // GENERAR TODO
    // ==========================================

    public void generarTodoPeriodo(String periodo) {

        List<EmpleadoPlanilla> empleados =
                planillaRepository.findByActivo(1);

        List<String> errores =
                new ArrayList<>();

        int procesados = 0;

        for (EmpleadoPlanilla planilla : empleados) {

            try {

                generar(
                        planilla.getEmpleadoId(),
                        periodo);

                procesados++;

            } catch (Exception e) {

                errores.add(
                        e.getMessage());
            }
        }

        // ==========================================
        // SI HUBO ERRORES
        // ==========================================

        if (!errores.isEmpty()) {

            throw new NegocioException(
                    errores.toString());
        }
    }

    // ==========================================
    // RESUMEN
    // ==========================================

    public ResumenPlanillaDto
    obtenerResumen(Long empleadoId,
                   String periodo) {

        MovimientoPlanilla mov =
                movimientoRepository
                        .findByEmpleadoIdAndPeriodoAndActivo(
                                empleadoId,
                                periodo,
                                1)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Planilla no encontrada"));

        ResumenPlanillaDto dto =
                new ResumenPlanillaDto();

        dto.setEmpleadoId(empleadoId);

        dto.setPeriodo(periodo);

        dto.setTotalIngresos(
                mov.getTotalIngresos());

        dto.setTotalDescuentos(
                mov.getTotalDescuentos());

        dto.setNetoPagar(
                mov.getNetoPagar());

        return dto;
    }
}