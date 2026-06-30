package com.indeci.rrhh.dto;

import java.math.BigDecimal;

public class LiquidacionQuintaDTO {

    private String idEmpleado;
    private String nombreCompleto;
    private String periodoCalculo;

    // Bloque 1 (RBA)
    private BigDecimal sueldoMensualProyectado;
    private BigDecimal gratificacionesProyectadas;
    private BigDecimal remuneracionesPercibidasAnteriores;
    private BigDecimal ingresosOtrosEmpleadores;
    private BigDecimal totalRentaBrutaAnual;

    // Bloque 2 (Deducción)
    private BigDecimal montoDeduccionUIT;
    private BigDecimal totalRentaNetaImponible;

    // Bloque 3 (Tramos)
    private BigDecimal tramo1;
    private BigDecimal tramo2;
    private BigDecimal tramo3;
    private BigDecimal tramo4;
    private BigDecimal tramo5;
    private BigDecimal impuestoAnualProyectado;

    // Bloque 4 (Prorrateo)
    private BigDecimal retencionesAcumuladasExternas;
    private BigDecimal saldoRetenerAnual;
    private BigDecimal divisorMes;
    private BigDecimal montoRetenerMes;

    public LiquidacionQuintaDTO() {
    }

    public String getIdEmpleado() {
        return idEmpleado;
    }

    public void setIdEmpleado(String idEmpleado) {
        this.idEmpleado = idEmpleado;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getPeriodoCalculo() {
        return periodoCalculo;
    }

    public void setPeriodoCalculo(String periodoCalculo) {
        this.periodoCalculo = periodoCalculo;
    }

    public BigDecimal getSueldoMensualProyectado() {
        return sueldoMensualProyectado;
    }

    public void setSueldoMensualProyectado(BigDecimal sueldoMensualProyectado) {
        this.sueldoMensualProyectado = sueldoMensualProyectado;
    }

    public BigDecimal getGratificacionesProyectadas() {
        return gratificacionesProyectadas;
    }

    public void setGratificacionesProyectadas(BigDecimal gratificacionesProyectadas) {
        this.gratificacionesProyectadas = gratificacionesProyectadas;
    }

    public BigDecimal getRemuneracionesPercibidasAnteriores() {
        return remuneracionesPercibidasAnteriores;
    }

    public void setRemuneracionesPercibidasAnteriores(BigDecimal remuneracionesPercibidasAnteriores) {
        this.remuneracionesPercibidasAnteriores = remuneracionesPercibidasAnteriores;
    }

    public BigDecimal getIngresosOtrosEmpleadores() {
        return ingresosOtrosEmpleadores;
    }

    public void setIngresosOtrosEmpleadores(BigDecimal ingresosOtrosEmpleadores) {
        this.ingresosOtrosEmpleadores = ingresosOtrosEmpleadores;
    }

    public BigDecimal getTotalRentaBrutaAnual() {
        return totalRentaBrutaAnual;
    }

    public void setTotalRentaBrutaAnual(BigDecimal totalRentaBrutaAnual) {
        this.totalRentaBrutaAnual = totalRentaBrutaAnual;
    }

    public BigDecimal getMontoDeduccionUIT() {
        return montoDeduccionUIT;
    }

    public void setMontoDeduccionUIT(BigDecimal montoDeduccionUIT) {
        this.montoDeduccionUIT = montoDeduccionUIT;
    }

    public BigDecimal getTotalRentaNetaImponible() {
        return totalRentaNetaImponible;
    }

    public void setTotalRentaNetaImponible(BigDecimal totalRentaNetaImponible) {
        this.totalRentaNetaImponible = totalRentaNetaImponible;
    }

    public BigDecimal getTramo1() {
        return tramo1;
    }

    public void setTramo1(BigDecimal tramo1) {
        this.tramo1 = tramo1;
    }

    public BigDecimal getTramo2() {
        return tramo2;
    }

    public void setTramo2(BigDecimal tramo2) {
        this.tramo2 = tramo2;
    }

    public BigDecimal getTramo3() {
        return tramo3;
    }

    public void setTramo3(BigDecimal tramo3) {
        this.tramo3 = tramo3;
    }

    public BigDecimal getTramo4() {
        return tramo4;
    }

    public void setTramo4(BigDecimal tramo4) {
        this.tramo4 = tramo4;
    }

    public BigDecimal getTramo5() {
        return tramo5;
    }

    public void setTramo5(BigDecimal tramo5) {
        this.tramo5 = tramo5;
    }

    public BigDecimal getImpuestoAnualProyectado() {
        return impuestoAnualProyectado;
    }

    public void setImpuestoAnualProyectado(BigDecimal impuestoAnualProyectado) {
        this.impuestoAnualProyectado = impuestoAnualProyectado;
    }

    public BigDecimal getRetencionesAcumuladasExternas() {
        return retencionesAcumuladasExternas;
    }

    public void setRetencionesAcumuladasExternas(BigDecimal retencionesAcumuladasExternas) {
        this.retencionesAcumuladasExternas = retencionesAcumuladasExternas;
    }

    public BigDecimal getSaldoRetenerAnual() {
        return saldoRetenerAnual;
    }

    public void setSaldoRetenerAnual(BigDecimal saldoRetenerAnual) {
        this.saldoRetenerAnual = saldoRetenerAnual;
    }

    public BigDecimal getDivisorMes() {
        return divisorMes;
    }

    public void setDivisorMes(BigDecimal divisorMes) {
        this.divisorMes = divisorMes;
    }

    public BigDecimal getMontoRetenerMes() {
        return montoRetenerMes;
    }

    public void setMontoRetenerMes(BigDecimal montoRetenerMes) {
        this.montoRetenerMes = montoRetenerMes;
    }
}
