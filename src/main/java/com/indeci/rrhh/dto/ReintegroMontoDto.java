package com.indeci.rrhh.dto;

import com.indeci.rrhh.entity.MotivoReintegro;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Alta de reintegro/devengado (Modelo B). Blindaje Poka-Yoke: el backend NO confía
 * en el frontend — valida en el contrato antes de tocar la base de datos.
 */
@Data
public class ReintegroMontoDto {

    @NotNull(message = "El empleado es obligatorio.")
    private Long empleadoId;

    @NotBlank(message = "El período de destino es obligatorio.")
    private String periodoDestino;

    // Origen opcional: en devengados judiciales/reposición la trazabilidad la da el
    // N° de resolución (sustento), no siempre un movimiento origen identificado.
    private String periodoOrigen;
    private Long movimientoOrigenId;
    private String conceptoOrigenCodigo;

    @NotNull(message = "El monto es obligatorio.")
    @Positive(message = "El monto debe ser mayor a cero.")
    private BigDecimal monto;

    /** Contrato rígido: un valor fuera del enum se rechaza con 400 en la deserialización. */
    @NotNull(message = "El motivo del reintegro es obligatorio.")
    private MotivoReintegro motivo;

    @NotBlank(message = "El N° de Resolución o Mandato Judicial es obligatorio para la trazabilidad de auditoría.")
    private String sustento;

    /**
     * Candado de trazabilidad (Track B / Contraloría): un RETROACTIVO o
     * DIFERENCIA_REMUNERATIVA proviene de un pago previo del sistema, por lo que
     * exige período y concepto de origen. Los devengados judiciales / reposición
     * no lo exigen (su trazabilidad es la resolución).
     */
    @AssertTrue(message = "Para ajustes retroactivos o diferencias salariales es obligatorio especificar el periodo y concepto de origen.")
    public boolean isOrigenConsistenteConMotivo() {
        if (motivo == MotivoReintegro.RETROACTIVO
                || motivo == MotivoReintegro.DIFERENCIA_REMUNERATIVA) {
            return periodoOrigen != null && !periodoOrigen.isBlank()
                    && conceptoOrigenCodigo != null && !conceptoOrigenCodigo.isBlank();
        }
        return true;
    }
}
