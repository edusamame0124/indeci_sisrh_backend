package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.entity.CalculoSnapshot;
import com.indeci.rrhh.repository.CalculoSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * FASE 2 — Trazabilidad: persiste los parámetros/magnitudes con que el motor
 * obtuvo un cálculo (snapshot reproducible).
 *
 * <p><b>Solo añadido:</b> ningún cálculo del motor depende de leer estas filas.
 * Se escriben como efecto lateral de la generación de planilla y las consume,
 * en solo lectura, la pantalla de explicación.</p>
 *
 * <p>Reproducibilidad: antes de regenerar un período el motor llama a
 * {@link #desactivarPrevios(Long, String)}; así queda un único conjunto
 * vigente por par empleado/período.</p>
 */
@Service
@RequiredArgsConstructor
public class CalculoSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(CalculoSnapshotService.class);

    public static final String REGLA_GENERAL = "GENERAL";
    public static final String REGLA_IR4TA_CAS = "IR4TA_CAS";
    public static final String REGLA_IR5TA = "IR5TA";
    public static final String REGLA_SUBSIDIO = "SUBSIDIO";
    public static final String REGLA_ESSALUD = "ESSALUD";
    /** Feature 016 — Liquidación de CTS Trunca (requiere V012_15 en el CK de REGLA). */
    public static final String REGLA_CTS = "CTS";

    private final CalculoSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Desactiva (ACTIVO=0) los snapshots vigentes del par empleado/período.
     * Idempotente: si no hay filas, devuelve 0.
     */
    @Transactional
    public int desactivarPrevios(Long empleadoId, String periodo) {
        if (empleadoId == null || periodo == null || periodo.isBlank()) {
            return 0;
        }
        return repository.desactivarVigentes(empleadoId, periodo);
    }

    /**
     * Persiste un snapshot. El builder encapsula la construcción del payload
     * JSON; la serialización nunca propaga errores al motor (si falla, se graba
     * sin payload y se loguea — la trazabilidad es auxiliar, no debe romper el
     * cálculo oficial).
     */
    @Transactional
    public CalculoSnapshot registrar(Registro registro) {
        CalculoSnapshot snap = new CalculoSnapshot();
        snap.setEmpleadoId(registro.empleadoId);
        snap.setPeriodo(registro.periodo);
        snap.setMovimientoPlanillaId(registro.movimientoPlanillaId);
        snap.setRegla(registro.regla);
        snap.setBaseCalculo(registro.baseCalculo);
        snap.setResultado(registro.resultado);
        snap.setFormula(registro.formula);
        snap.setVersionParametros(registro.versionParametros);
        snap.setParametrosJson(serializar(registro.parametros));
        snap.setActivo(1);
        snap.setCreatedAt(LocalDateTime.now());
        return repository.save(snap);
    }

    /** Snapshots vigentes del par empleado/período (solo lectura). */
    @Transactional(readOnly = true)
    public List<CalculoSnapshot> listar(Long empleadoId, String periodo) {
        if (empleadoId == null || periodo == null || periodo.isBlank()) {
            return List.of();
        }
        return repository.findByEmpleadoIdAndPeriodoAndActivoOrderByReglaAscIdAsc(
                empleadoId, periodo, 1);
    }

    private String serializar(Map<String, Object> parametros) {
        if (parametros == null || parametros.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parametros);
        } catch (JsonProcessingException e) {
            log.warn("No se pudo serializar parámetros de snapshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Crea un builder de snapshot para la regla indicada. Acumula los
     * parámetros específicos en orden de inserción.
     */
    public static Registro registro(Long empleadoId, String periodo, String regla) {
        return new Registro(empleadoId, periodo, regla);
    }

    /**
     * Builder mutable y fluido para construir un snapshot en el call site del
     * motor sin exponer la entidad ni el mapa de parámetros.
     */
    public static final class Registro {
        private final Long empleadoId;
        private final String periodo;
        private final String regla;
        private final Map<String, Object> parametros = new LinkedHashMap<>();
        private Long movimientoPlanillaId;
        private BigDecimal baseCalculo;
        private BigDecimal resultado;
        private String formula;
        private String versionParametros;

        private Registro(Long empleadoId, String periodo, String regla) {
            this.empleadoId = empleadoId;
            this.periodo = periodo;
            this.regla = regla;
        }

        public Registro movimiento(Long movimientoPlanillaId) {
            this.movimientoPlanillaId = movimientoPlanillaId;
            return this;
        }

        public Registro base(BigDecimal baseCalculo) {
            this.baseCalculo = baseCalculo;
            return this;
        }

        public Registro resultado(BigDecimal resultado) {
            this.resultado = resultado;
            return this;
        }

        public Registro formula(String formula) {
            this.formula = formula;
            return this;
        }

        public Registro version(String versionParametros) {
            this.versionParametros = versionParametros;
            return this;
        }

        /** Agrega un parámetro al payload JSON (ignora claves nulas). */
        public Registro param(String clave, Object valor) {
            if (clave != null) {
                this.parametros.put(clave, valor);
            }
            return this;
        }
    }
}
