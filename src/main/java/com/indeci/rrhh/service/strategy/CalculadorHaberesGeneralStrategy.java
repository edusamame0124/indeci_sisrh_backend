package com.indeci.rrhh.service.strategy;

import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.service.Ir4taControlAnualService;
import com.indeci.rrhh.service.ParametroRemunerativoService;
import com.indeci.rrhh.service.Suspension4taService;
import com.indeci.rrhh.service.GeneradorPlanillaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CalculadorHaberesGeneralStrategy implements CalculadorConceptoStrategy {

    private final Suspension4taService suspension4taService;
    private final Ir4taControlAnualService ir4taControlAnualService;

    @Override
    public boolean aplica(String codigoRegimen, String codigoConcepto) {
        // En esta etapa asumimos que el legacy procesa PLA_HABERES para cualquier régimen
        return "PLA_HABERES".equals(codigoConcepto);
    }

    @Override
    public void ejecutarCalculo(ContextoCalculoPlanilla contexto) {
        GeneradorPlanillaService motor = contexto.getMotorLegacy();

        BigDecimal totalIngresos    = BigDecimal.ZERO;
        BigDecimal totalDescuentos  = BigDecimal.ZERO;
        BigDecimal ingresosMensualesPermanentes = BigDecimal.ZERO;
        BigDecimal baseImponiblePens = BigDecimal.ZERO;
        BigDecimal baseImponibleEss  = BigDecimal.ZERO;
        BigDecimal ir4taCas = BigDecimal.ZERO;

        // 5. Conceptos remunerativos calculados por el motor
        GeneradorPlanillaService.RemunerativosResult rem =
                motor.calcularRemunerativos(contexto.getMovimiento(), contexto.getPlanilla(), contexto.getEmpleado(), contexto.getAnioFiscal(), contexto.getOverrideSueldoBasico());
        totalIngresos     = totalIngresos.add(rem.totalRemunerativo);
        ingresosMensualesPermanentes =
                ingresosMensualesPermanentes.add(rem.totalRemunerativo);
        baseImponiblePens = baseImponiblePens.add(rem.baseAportePension);
        baseImponibleEss  = baseImponibleEss.add(rem.baseEssalud);

        // 6. No remunerativos automáticos (Etapa 1: vacío. Aguinaldos/gratificaciones a Etapa 2)
        // calcularNoRemunerativos(movimiento, planilla, periodo); // placeholder

        // Régimen laboral (necesario para PASO 7 validación normativa F1.5b
        // y PASO 9 tope EsSalud CAS F1.6). Se resuelve una sola vez.
        String regimenLaboralCodigo =
                motor.resolverRegimenLaboralCodigo(contexto.getPlanilla().getRegimenLaboralId());

        // 7. Conceptos manuales (EmpleadoConcepto) — valida LEY-01 + F1.5b:
        //    régimen aplicable del concepto + prorrateo por días laborados.
        GeneradorPlanillaService.ManualesResult manuales =
                motor.aplicarConceptosManuales(contexto.getMovimiento(), contexto.getPlanilla(), regimenLaboralCodigo, contexto.getPeriodo());
        totalIngresos     = totalIngresos.add(manuales.ingresos);
        totalDescuentos   = totalDescuentos.add(manuales.descuentos);
        ingresosMensualesPermanentes = ingresosMensualesPermanentes
                .add(manuales.ingresosMensualesPermanentes);
        baseImponiblePens = baseImponiblePens.add(manuales.baseAportePension);
        baseImponibleEss  = baseImponibleEss.add(manuales.baseEssalud);

        if (motor.isMotorV3ProrrateoEnabled()) {
            BigDecimal reintegro =
                    motor.calcularReintegro(contexto.getEmpleadoId(), contexto.getPeriodo(), totalIngresos);
            if (reintegro.signum() > 0) {
                totalIngresos     = totalIngresos.add(reintegro);
                baseImponiblePens = baseImponiblePens.add(reintegro);
                baseImponibleEss  = baseImponibleEss.add(reintegro);
            }
        }

        BigDecimal ingresoSubsidio =
                motor.registrarSubsidiosEventos(contexto.getEmpleadoId(), contexto.getPeriodo(), contexto.getMovimiento(), contexto.getPlanilla());
        totalIngresos = totalIngresos.add(ingresoSubsidio);

        // Nota Track B: el AGUINALDO se genera en un PROCESO APARTE (tipo de planilla
        // AGUINALDO), no dentro de la planilla regular. Reglas por régimen
        // (SERVIR 100% / CAS % manual / 276 fijo) → ver AguinaldoService. Aquí no se graba.

        BigDecimal descuentoAsistencia =
                motor.calcularDescuentoAsistencia(contexto.getMovimiento(), contexto.getEmpleadoId(), contexto.getPeriodo());
        totalDescuentos = totalDescuentos.add(descuentoAsistencia);

        BigDecimal aportePension = BigDecimal.ZERO;
        if (contexto.getPensionOpt().isPresent()) {
            aportePension = motor.calcularAportePensionario(
                    contexto.getMovimiento(), contexto.getPensionOpt().get(), baseImponiblePens, contexto.getAnioFiscal());
            totalDescuentos = totalDescuentos.add(aportePension);
        }

        BigDecimal retencion5ta = motor.calcular5taCategoria(
                contexto.getMovimiento(), contexto.getPlanilla(), baseImponiblePens, contexto.getAnioFiscal());
        totalDescuentos = totalDescuentos.add(retencion5ta);

        if (motor.isMotorV3ProrrateoEnabled() && GeneradorPlanillaService.esRegimenCas(regimenLaboralCodigo)) {
            LocalDate fechaDevengue =
                    ParametroRemunerativoService.periodoToFechaInicio(contexto.getPeriodo());
            Suspension4taVigenteDto suspension =
                    suspension4taService.consultarVigente(contexto.getEmpleadoId(), fechaDevengue);

            BigDecimal montoNoAfectoIr4ta = BigDecimal.ZERO;
            BigDecimal sueldoBasicoVinculacion = contexto.getOverrideSueldoBasico() != null
                    ? contexto.getOverrideSueldoBasico()
                    : motor.resolverBaseRemunerativa(contexto.getPlanilla(), contexto.getMovimiento().getPeriodo());
            // Fase 3 / F1.9 — la base del IR4ta debe seguir el MISMO prorrateo que
            // la remuneración pagada: días de vínculo (cese/alta) MENOS días de
            // subsidio. Los subsidios de EsSalud son INAFECTOS al Impuesto a la
            // Renta (Art. 18 TUO LIR) → jamás retener sobre el monto subsidiado.
            sueldoBasicoVinculacion = motor.prorratearBaseTributariaPorVinculoYSubsidio(
                    sueldoBasicoVinculacion, contexto.getPlanilla(), contexto.getMovimiento().getPeriodo());
            BigDecimal baseIr4ta = sueldoBasicoVinculacion
                    .subtract(montoNoAfectoIr4ta)
                    .subtract(descuentoAsistencia)
                    .max(BigDecimal.ZERO);

            boolean suspendeEfectiva = suspension.vigente();
            Ir4taControlAnualService.MotorDecision controlTope =
                    ir4taControlAnualService.evaluarEnMotor(
                            contexto.getEmpleadoId(), contexto.getAnioFiscal(), contexto.getPeriodo(), baseIr4ta,
                            suspension.vigente(), null);
            if (controlTope != null && controlTope.aplicarRetencionPeseASuspension()) {
                suspendeEfectiva = false;
            }

            ir4taCas = motor.calcular4taCategoriaCAS(
                    baseIr4ta, regimenLaboralCodigo, contexto.getAnioFiscal(), suspendeEfectiva);
            if (ir4taCas.signum() > 0) {
                ConceptoPlanilla conceptoIr4ta = motor.conceptoIr4taCas();
                String obs = String.format(
                        "IR4ta CAS NORMAL | base=%s | noAfecto=%s | baseFinal=%s | tasa=8%% | "
                                + "tributoSUNAT=3042 | constancia=%s | periodo=%s",
                        sueldoBasicoVinculacion.setScale(2, RoundingMode.HALF_UP),
                        montoNoAfectoIr4ta.setScale(2, RoundingMode.HALF_UP),
                        baseIr4ta.setScale(2, RoundingMode.HALF_UP),
                        suspension.nroConstancia() == null ? "-" : suspension.nroConstancia(),
                        contexto.getPeriodo());
                motor.grabarDetalle(contexto.getMovimiento().getId(), conceptoIr4ta, ir4taCas, obs);
                totalDescuentos = totalDescuentos.add(ir4taCas);
            }

            motor.registrarSnapshotIr4ta(contexto.getEmpleadoId(), contexto.getPeriodo(), contexto.getMovimiento().getId(), contexto.getAnioFiscal(),
                    sueldoBasicoVinculacion, baseIr4ta, ir4taCas, suspension);
        }

        BigDecimal copagoEps = motor.calcularEssaludEmpleador(
                contexto.getMovimiento(), contexto.getEmpleado(), baseImponibleEss, contexto.getAnioFiscal(), regimenLaboralCodigo,
                contexto.getEmpleadoId(), contexto.getPeriodo());
        totalDescuentos = totalDescuentos.add(copagoEps);

        BigDecimal retencionRenta = retencion5ta.add(ir4taCas);
        motor.calcularTotalesYCUC(contexto.getMovimiento(), totalIngresos, totalDescuentos,
                ingresosMensualesPermanentes,
                retencionRenta, aportePension, manuales.descuentoJudicial);

        motor.crearConciliacionAirhsp(contexto.getMovimiento(), contexto.getEmpleado(), contexto.getPeriodoPlanilla(), totalIngresos);

        motor.registrarSnapshotGeneral(contexto.getEmpleadoId(), contexto.getPeriodo(), contexto.getMovimiento().getId(), contexto.getAnioFiscal(),
                contexto.getPlanilla(), regimenLaboralCodigo, totalIngresos, totalDescuentos);
    }
}
