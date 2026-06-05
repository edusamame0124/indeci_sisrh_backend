package com.indeci.rrhh.dto.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * P0 — Fila de la Planilla CAS Consolidada (spec SPEC_PLANILLA_CAS_EXPORT_COLUMNS).
 *
 * <p>Una instancia = una fila del XLSX. Cruza 9 entidades (Persona, Empleado,
 * EmpleadoPlanilla, EmpleadoBanco, EmpleadoPension, Suspension4ta,
 * AsistenciaCabecera, MovimientoPlanilla[+Detalle], CalculoSnapshot).</p>
 *
 * <p><b>Regla:</b> los campos que el sistema aún no captura quedan {@code null}
 * y el writer los exporta como celda vacía. NO se inventan datos ni se sustituye
 * null por 0 salvo cuando la columna lo indica explícitamente.</p>
 */
@Data
public class PlanillaConsolidadaRowDto {

    // ─────────── BLOQUE 1: Datos personales ──────────────────────────
    private Integer nro;
    private String dni;
    private String ruc;
    private String apellidosNombres;
    private LocalDate fechaNacimiento;
    private Integer edad;
    private String sexo;
    private String estadoCivil;
    private Long personaId;
    private String celular;
    private String correo;
    private String direccion;
    private String distrito;
    private String provincia;
    private String departamento;

    // ─────────── BLOQUE 2: Datos laborales ───────────────────────────
    private String estadoLaboral;
    private String estadoLsgr;                 // VACIO_SIN_FUENTE
    private String afiliadoSindicato;          // VACIO_SIN_FUENTE
    private String codigoSisper;
    private String registroAirhsp;
    private BigDecimal montoAirhsp;
    private String condicionAirhsp;            // VACIO_DECISION_RRHH
    private String cargo;
    private String dependencia;
    private String abreviaturaDependencia;     // VACIO_DECISION_RRHH
    private String condicionLaboral;
    private String condicionLaboralReportes;   // VACIO_DECISION_RRHH
    private LocalDate fechaIngreso;
    private LocalDate fechaTermino;

    // ─────────── BLOQUE 3: Banco / Abono ─────────────────────────────
    private String banco;
    private String numeroCuenta;
    private String cci;
    private String numeroCuentaTesoreria;
    private String estadoCuentaBancaria;
    private String cuentaPrincipal;
    private LocalDate fechaRegistroCuenta;
    private String observacionCuenta;          // VACIO_SIN_FUENTE

    // ─────────── BLOQUE 4: Régimen pensionario ───────────────────────
    private String regimenPensionario;
    private String afp;
    private String onp;
    private String tipoComision;
    private BigDecimal comisionPct;
    private String cuspp;
    private String pensionistaSinRegimenRetiro;
    private String estadoConfigPension;
    private LocalDate fechaInicioRegPension;
    private String observacionPension;         // VACIO_SIN_FUENTE

    // ─────────── BLOQUE 5: Suspensión 4ta ────────────────────────────
    private LocalDate fechaSuspension;
    private String nroOperacion;
    private Integer suspensionFlag;
    private String suspensionRenta4ta;
    private LocalDate fechaEmisionConstancia;
    private LocalDate fechaInicioVigencia;
    private LocalDate fechaFinVigencia;
    private String estadoSuspension;
    private String observacionSuspension;

    // ─────────── BLOQUE 6: Presupuesto / AIRHSP ──────────────────────
    private String meta2026;
    private String centroCosto2026;
    private String categoriaPresupuestal;      // VACIO_SIN_FUENTE
    private String producto;                   // VACIO_SIN_FUENTE
    private String actividad;                  // VACIO_SIN_FUENTE
    private String finalidad;                  // VACIO_SIN_FUENTE
    private String fuenteFinanciamiento;
    private String clasificadorGasto;          // VACIO_SIN_FUENTE
    private String secuenciaFuncional;         // VACIO_SIN_FUENTE
    private String unidadOrganicaPresupuestal; // VACIO_SIN_FUENTE

    // ─────────── BLOQUE 7: Contrato / Plaza ──────────────────────────
    private String numeroContrato;
    private String plaza;                      // VACIO_SIN_FUENTE
    private String condicionLaboralContrato;
    private LocalDate fechaIngresoContrato;
    private LocalDate fechaTerminoContrato;
    private BigDecimal montoContrato;
    private String estadoContrato;
    private String tipoContrato;
    private String observacionContrato;        // VACIO_SIN_FUENTE

    // ─────────── BLOQUE 8: Remuneración mensual / DS ─────────────────
    private BigDecimal montoContratoRem;
    private BigDecimal ds311Mensual;
    private BigDecimal ds313Mensual;
    private BigDecimal ds265Mensual;
    private BigDecimal ds279Mensual;
    private BigDecimal ds327Mensual;
    private BigDecimal remuneracionMensual;
    private Integer diasLaborados;
    private BigDecimal remProporcionalContrato;
    private BigDecimal ds311Proporcional;      // VACIO_CALCULO_PENDIENTE
    private BigDecimal ds313Proporcional;      // VACIO_CALCULO_PENDIENTE
    private BigDecimal ds265Proporcional;      // VACIO_CALCULO_PENDIENTE
    private BigDecimal ds279Proporcional;      // VACIO_CALCULO_PENDIENTE
    private BigDecimal ds327Proporcional;      // VACIO_CALCULO_PENDIENTE
    private BigDecimal pagoDiferencial;
    private Integer diasReintegro;
    private BigDecimal montoReintegro;
    private BigDecimal totalRemuneracionMensual;

    // ─────────── BLOQUE 9: Asistencia ────────────────────────────────
    private Integer diasNoLaborados;
    private BigDecimal reduccionTardanzas;
    private BigDecimal permisosPersonales;       // VACIO_SIN_FUENTE
    private BigDecimal feriadosCompensables;     // VACIO_SIN_FUENTE
    private BigDecimal inasistenciaFaltas;
    private BigDecimal descuentoTotalAsistencia;
    private Integer minutosTardanza;
    private Integer minutosPermisosPersonales;   // VACIO_SIN_FUENTE
    private Integer minutosFeriadosCompensables; // VACIO_SIN_FUENTE
    private Integer diasDescuentoInasistencia;
    private String observacionAsistencia;

    // ─────────── BLOQUE 10: Base imponible / Aguinaldo ───────────────
    private BigDecimal baseImponibleIngresosEgresos;
    private BigDecimal aguinaldoJulioDiciembre;  // VACIO_CALCULO_PENDIENTE
    private BigDecimal remuneracionBruta;
    private BigDecimal baseAfpOnp;
    private BigDecimal baseEssalud;
    private BigDecimal baseIr4ta;
    private BigDecimal baseNetaValidacion50;

    // ─────────── BLOQUE 11: EsSalud / EPS ────────────────────────────
    private BigDecimal aporteEssalud;
    private BigDecimal essaludSinEps9pct;        // VACIO_CALCULO_PENDIENTE
    private BigDecimal essalud675pct;
    private BigDecimal eps225pct;
    private BigDecimal copagoEpsTrabajador;
    private String estadoEssalud;
    private BigDecimal tope45pctUit;
    private BigDecimal essaludMinimo;
    private BigDecimal essaludMaximo;            // VACIO_CALCULO_PENDIENTE

    // ─────────── BLOQUE 12: IR 4ta categoría ─────────────────────────
    private String indicadorSuspension;
    private BigDecimal ir4taRemuneracion;
    private BigDecimal ir4taAguinaldo;          // VACIO_DECISION_RRHH
    private BigDecimal ir4taTotal;
    private String codigoInternoIr4ta;
    private String codigoTributoSunat;
    private BigDecimal baseInafectaMensualIr4ta;
    private BigDecimal tasaIr4ta;
    private String nroOperacionSuspension;
    private String estadoSuspension4ta;
    private String observacionIr4ta;

    // ─────────── BLOQUE 13: AFP / ONP ────────────────────────────────
    private BigDecimal aporteAfp10pct;
    private BigDecimal comisionAfpPct;
    private BigDecimal comisionAfpMonto;
    private BigDecimal prima137pct;
    private BigDecimal totalAfp;
    private BigDecimal onpSnp;
    private String regimenPensionarioBlq13;
    private String afpBlq13;
    private String tipoComisionBlq13;
    private String cusppBlq13;
    private BigDecimal topeSeguroAfp;           // VACIO_CALCULO_PENDIENTE
    private BigDecimal baseAfp;
    private BigDecimal baseOnp;

    // ─────────── BLOQUE 14: Descuentos terceros (SISPER) ─────────────
    private BigDecimal descuentoJudicial;
    private BigDecimal cooperativaRehabilitadora;
    private BigDecimal otrosDescuentos;
    private BigDecimal fesalud;
    private BigDecimal interSura;
    private BigDecimal masVidaEssalud;
    private BigDecimal coopSanMiguel;
    private BigDecimal coopSerfinco;
    private BigDecimal bancom;                  // VACIO_DECISION_RRHH (código)
    private BigDecimal rimacSeguros;
    private BigDecimal copagoEpsDescuento;
    private BigDecimal cuotaSindical;           // VACIO_DECISION_RRHH (código)
    private BigDecimal otrosSeguros;            // VACIO_DECISION_RRHH
    private BigDecimal otrosPrestamos;          // VACIO_DECISION_RRHH
    private BigDecimal totalDescuentosTerceros;

    // ─────────── BLOQUE 15: Total descuentos / Neto ──────────────────
    private BigDecimal totalDescuento;
    private BigDecimal netoPagar;
    private BigDecimal remuneracionBrutaBlq15;
    private BigDecimal totalAportesTrabajador;
    private BigDecimal totalDescuentosLegales;
    private BigDecimal totalDescuentosJudiciales;
    private BigDecimal totalDescuentosTercerosBlq15;

    // ─────────── BLOQUE 16: Validación 50% ───────────────────────────
    private BigDecimal cincuentaPctRemuneracion;
    private String estado50pct;
    private BigDecimal baseValidacion50;
    private BigDecimal descuentosLegalesConsiderados;
    private BigDecimal descuentoJudicialConsiderado;
    private BigDecimal margenDisponibleTerceros;
    private String observacionValidacion50;    // VACIO_SIN_FUENTE

    // ─────────── BLOQUE 17: Validación EsSalud ───────────────────────
    private String estadoEssaludBlq17;
    private BigDecimal baseEssaludBlq17;
    private BigDecimal essaludCalculado;
    private BigDecimal essaludMinimoBlq17;
    private BigDecimal essaludMaximoBlq17;      // VACIO_CALCULO_PENDIENTE
    private BigDecimal tope45pctUitBlq17;
    private String observacionEssalud;          // VACIO_SIN_FUENTE

    // ─────────── BLOQUE 18: Comparativo periodo anterior ─────────────
    private String planillaAnterior;            // VACIO_CALCULO_PENDIENTE
    private BigDecimal netoPeriodoAnterior;     // VACIO_CALCULO_PENDIENTE
    private BigDecimal netoActual;
    private BigDecimal diferencia;              // VACIO_CALCULO_PENDIENTE
    private String motivoDiferencia;            // VACIO_SIN_FUENTE
    private String tipoDiferencia;              // VACIO_DECISION_RRHH
    private String requiereRevision;            // VACIO_DECISION_RRHH
    private String observacionRrhhComp;         // VACIO_SIN_FUENTE

    // ─────────── BLOQUE 19: Observaciones / Auditoría ────────────────
    private String observacionPlanilla;
    private String observacionTrabajador;       // VACIO_SIN_FUENTE
    private String observacionRrhh;             // VACIO_SIN_FUENTE
    private String usuarioGeneracion;
    private LocalDateTime fechaGeneracion;
    private String usuarioExportacion;
    private LocalDateTime fechaExportacion;
    private String estadoPeriodo;
    private String estadoRegistro;
    private String motivoExclusion;             // VACIO_SIN_FUENTE
}
