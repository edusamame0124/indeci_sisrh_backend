package com.indeci.rrhh.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Trazabilidad del calculo de subsidios por evento medico del trabajador.
 *
 * <p>Esta tabla no reemplaza a {@code INDECI_MOVIMIENTO_PLANILLA_DET}; conserva
 * la evidencia de dias y montos que sustentan subsidio EsSalud y diferencial CAS.</p>
 */
@Entity
@Table(name = "INDECI_SUBSIDIO_EVENTO_CALCULO")
@Data
public class SubsidioEventoCalculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "EMPLEADO_EVENTO_ID", nullable = false)
    private Long empleadoEventoId;

    @Column(name = "MOVIMIENTO_PLANILLA_ID")
    private Long movimientoPlanillaId;

    @Column(name = "PERIODO", length = 6)
    private String periodo;

    @Column(name = "ANIO_CALENDARIO", nullable = false)
    private Integer anioCalendario;

    @Column(name = "TIPO_SUBSIDIO", nullable = false, length = 20)
    private String tipoSubsidio;

    @Column(name = "FECHA_INICIO_DESCANSO", nullable = false)
    private LocalDate fechaInicioDescanso;

    @Column(name = "FECHA_FIN_DESCANSO", nullable = false)
    private LocalDate fechaFinDescanso;

    @Column(name = "DIAS_DESCANSO", nullable = false)
    private Integer diasDescanso;

    @Column(name = "DIAS_ACUMULADOS_PREVIOS", nullable = false)
    private Integer diasAcumuladosPrevios;

    @Column(name = "DIAS_A_CARGO_ENTIDAD", nullable = false)
    private Integer diasACargoEntidad;

    @Column(name = "DIAS_SUBSIDIO_ESSALUD", nullable = false)
    private Integer diasSubsidioEssalud;

    @Column(name = "CITT_NUMERO", length = 50)
    private String cittNumero;

    @Column(name = "DOCUMENTO_MEDICO_REFERENCIA", length = 100)
    private String documentoMedicoReferencia;

    @Column(name = "CODIGO_PLAME_SUBSIDIO", length = 6)
    private String codigoPlameSubsidio;

    @Column(name = "CODIGO_PLAME_DIFERENCIAL", length = 6)
    private String codigoPlameDiferencial;

    @Column(name = "SUBSIDIO_TOTAL_100", precision = 18, scale = 2)
    private BigDecimal subsidioTotal100;

    @Column(name = "BASE_RECONOCIDA_ESSALUD", precision = 18, scale = 2)
    private BigDecimal baseReconocidaEssalud;

    @Column(name = "SUBSIDIO_DIARIO_ESSALUD", precision = 18, scale = 2)
    private BigDecimal subsidioDiarioEssalud;

    @Column(name = "SUBSIDIO_ESSALUD", precision = 18, scale = 2)
    private BigDecimal subsidioEssalud;

    @Column(name = "PAGO_DIFERENCIAL", precision = 18, scale = 2)
    private BigDecimal pagoDiferencial;

    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;
}
