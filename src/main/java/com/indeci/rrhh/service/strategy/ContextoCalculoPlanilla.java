package com.indeci.rrhh.service.strategy;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPension;
import com.indeci.rrhh.service.GeneradorPlanillaService;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Optional;

@Data
@Builder
public class ContextoCalculoPlanilla {
    private Long empleadoId;
    private String periodo;
    private String regimenLaboralCodigo;
    private int anioFiscal;
    private EmpleadoPlanilla planilla;
    private Empleado empleado;
    private MovimientoPlanilla movimiento;
    private PeriodoPlanilla periodoPlanilla;
    private Optional<EmpleadoPension> pensionOpt;
    private BigDecimal overrideSueldoBasico;
    
    // Referencia temporal para invocar métodos legados extraídos
    private GeneradorPlanillaService motorLegacy;
}
