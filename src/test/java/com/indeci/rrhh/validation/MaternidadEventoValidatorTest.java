package com.indeci.rrhh.validation;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EventoPeriodoDto;
import com.indeci.rrhh.service.support.DistribucionMensualCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaternidadEventoValidatorTest {

    @Test
    void validar_maternidad_98_un_mes_ok() {
        EventoPeriodoDto dto = dtoMaternidad98(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));
        assertThatCode(() -> MaternidadEventoValidator.validar(dto))
                .doesNotThrowAnyException();
    }

    @Test
    void validar_maternidad_128_multimes_ok() {
        EventoPeriodoDto dto = dtoMaternidad128(
                LocalDate.of(2026, 5, 1));
        assertThatCode(() -> MaternidadEventoValidator.validar(dto))
                .doesNotThrowAnyException();
    }

    @Test
    void validar_duracion_96_lanza() {
        EventoPeriodoDto dto = dtoMaternidad98(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));
        dto.setDuracionLegal(96);
        dto.setFechaFin(LocalDate.of(2026, 8, 5));
        dto.setDistribucionMensual(
                DistribucionMensualCalculator.calcular(dto.getFechaInicio(), dto.getFechaFin()));

        assertThatThrownBy(() -> MaternidadEventoValidator.validar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("98 días");
    }

    @Test
    void validar_128_sin_motivo_lanza() {
        EventoPeriodoDto dto = dtoMaternidad128(LocalDate.of(2026, 5, 1));
        dto.setMotivoExtension(null);

        assertThatThrownBy(() -> MaternidadEventoValidator.validar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("motivo de extensión");
    }

    @Test
    void validar_desglose_incorrecto_lanza() {
        EventoPeriodoDto dto = dtoMaternidad98(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));
        var tramos = DistribucionMensualCalculator.calcular(
                dto.getFechaInicio(), dto.getFechaFin());
        tramos.get(0).setDiasSubsidio(90);
        dto.setDistribucionMensual(tramos);

        assertThatThrownBy(() -> MaternidadEventoValidator.validar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("desglose");
    }

    private EventoPeriodoDto dtoMaternidad98(LocalDate inicio, LocalDate fpp) {
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);
        EventoPeriodoDto dto = new EventoPeriodoDto();
        dto.setDuracionLegal(98);
        dto.setFechaInicio(inicio);
        dto.setFechaFin(fin);
        dto.setFechaProbableParto(fpp);
        dto.setDifierePrenatalPostnatal("NO");
        dto.setTipoDocumento("CITT");
        dto.setNroCitt("CITT-001");
        dto.setFechaEmisionDoc(LocalDate.of(2026, 4, 20));
        dto.setDistribucionMensual(DistribucionMensualCalculator.calcular(inicio, fin));
        return dto;
    }

    private EventoPeriodoDto dtoMaternidad128(LocalDate inicio) {
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 128);
        EventoPeriodoDto dto = new EventoPeriodoDto();
        dto.setDuracionLegal(128);
        dto.setMotivoExtension("NACIMIENTO_MULTIPLE");
        dto.setFechaInicio(inicio);
        dto.setFechaFin(fin);
        dto.setFechaProbableParto(inicio);
        dto.setDifierePrenatalPostnatal("NO");
        dto.setTipoDocumento("CITT");
        dto.setNroCitt("CITT-002");
        dto.setFechaEmisionDoc(LocalDate.of(2026, 4, 20));
        dto.setDistribucionMensual(DistribucionMensualCalculator.calcular(inicio, fin));
        return dto;
    }
}
