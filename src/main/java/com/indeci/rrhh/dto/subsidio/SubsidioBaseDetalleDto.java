package com.indeci.rrhh.dto.subsidio;

import java.math.BigDecimal;

public record SubsidioBaseDetalleDto(
        String periodo,
        BigDecimal remuneracionReal,
        BigDecimal topeAplicado,
        BigDecimal baseComputable,
        String incidencia,
        String esManual,
        Long fuenteMovimientoId) {}
