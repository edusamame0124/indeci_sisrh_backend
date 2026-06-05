package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * F2.4 - Resultado del calculo de subsidio (maternidad o enfermedad).
 *
 * El quinto campo conserva el nombre historico `promedioMensual12Meses` por
 * compatibilidad binaria/fuente con llamadas existentes, pero funcionalmente
 * representa la base reconocida EsSalud: suma de hasta 12 meses topados a 45%
 * UIT. Usar {@link #baseReconocidaEssalud()} en codigo nuevo.
 */
public record SubsidioCalculadoDto(
        boolean aplica,
        String tipoSubsidio,
        int diasDescanso,
        int diasAcumuladosPrevios,
        int diasEntidad,
        int diasSubsidioEssalud,
        String codigoPlameSubsidio,
        String codigoPlameDiferencial,
        BigDecimal remuneracionDiaria,
        BigDecimal subtotalRemunerativo,
        BigDecimal promedioMensual12Meses,
        BigDecimal subsidioDiarioEssalud,
        BigDecimal subsidioEssalud,
        BigDecimal diferenciaAsumidaIndeci) {

    public BigDecimal baseReconocidaEssalud() {
        return promedioMensual12Meses;
    }

    public static SubsidioCalculadoDto noAplica() {
        return new SubsidioCalculadoDto(
                false, null, 0, 0, 0, 0, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
