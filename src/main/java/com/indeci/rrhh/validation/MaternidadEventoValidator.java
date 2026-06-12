package com.indeci.rrhh.validation;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EventoDistribucionMesDto;
import com.indeci.rrhh.dto.EventoPeriodoDto;
import com.indeci.rrhh.service.support.DistribucionMensualCalculator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validaciones normativas P0 para eventos de subsidio por maternidad.
 */
public final class MaternidadEventoValidator {

    private static final Set<Integer> DURACIONES_VALIDAS = Set.of(98, 128);

    private MaternidadEventoValidator() {
    }

    public static void validar(EventoPeriodoDto dto) {
        if (dto.getDuracionLegal() == null || !DURACIONES_VALIDAS.contains(dto.getDuracionLegal())) {
            throw new NegocioException(
                    "El descanso por maternidad debe registrarse por 98 días naturales "
                            + "o 128 días si corresponde extensión legal.");
        }
        if (dto.getDuracionLegal() == 96) {
            throw new NegocioException(
                    "El descanso por maternidad debe registrarse por 98 días naturales "
                            + "o 128 días si corresponde extensión legal.");
        }
        if (dto.getFechaProbableParto() == null) {
            throw new NegocioException(
                    "La fecha probable de parto es obligatoria para subsidio por maternidad.");
        }
        if (dto.getFechaInicio() == null) {
            throw new NegocioException(
                    "El evento de maternidad requiere fecha de inicio del descanso.");
        }
   /*     LocalDate finEsperada = DistribucionMensualCalculator.calcularFechaFin(
                dto.getFechaInicio(), dto.getDuracionLegal());
        if (!dto.getFechaFin().equals(finEsperada)) {
            throw new NegocioException(
                    "Las fechas no son coherentes con la duración legal del descanso "
                            + "(" + dto.getDuracionLegal() + " días naturales).");
        }*/
        if (dto.getDuracionLegal() == 128
                && (dto.getMotivoExtension() == null || dto.getMotivoExtension().isBlank())) {
            throw new NegocioException(
                    "Debe indicar el motivo de extensión para descansos de 128 días.");
        }
        if (dto.getDifierePrenatalPostnatal() == null || dto.getDifierePrenatalPostnatal().isBlank()) {
            throw new NegocioException(
                    "Indique si difiere el descanso prenatal al postnatal.");
        }
        validarDocumento(dto);
        validarDistribucion(dto);
    }

    private static void validarDocumento(EventoPeriodoDto dto) {
        if (dto.getTipoDocumento() == null || dto.getTipoDocumento().isBlank()) {
            throw new NegocioException(
                    "Seleccione el tipo de documento sustentatorio (CITT, certificado médico, etc.).");
        }
        if (dto.getNroCitt() == null || dto.getNroCitt().isBlank()) {
            throw new NegocioException(
                    "Ingrese el número de CITT o documento sustentatorio.");
        }
        if (dto.getFechaEmisionDoc() == null) {
            throw new NegocioException(
                    "La fecha de emisión del documento sustentatorio es obligatoria.");
        }
    }

    private static void validarDistribucion(EventoPeriodoDto dto) {
        List<EventoDistribucionMesDto> tramos = dto.getDistribucionMensual();
        if (tramos == null || tramos.isEmpty()) {
            throw new NegocioException(
                    "El desglose mensual del descanso es obligatorio para maternidad.");
        }
        int suma = DistribucionMensualCalculator.sumarDias(tramos);
        if (suma != dto.getDuracionLegal()) {
            throw new NegocioException(
                    "El desglose mensual no coincide con la duración legal ("
                            + suma + " días registrados, se esperaban "
                            + dto.getDuracionLegal() + ").");
        }
        List<EventoDistribucionMesDto> esperados = DistribucionMensualCalculator.calcular(
                dto.getFechaInicio(), dto.getFechaFin());
        if (tramos.size() != esperados.size()) {
            throw new NegocioException("El desglose mensual del descanso es inválido.");
        }
        Set<String> periodos = new HashSet<>();
        for (int i = 0; i < tramos.size(); i++) {
            EventoDistribucionMesDto t = tramos.get(i);
            EventoDistribucionMesDto e = esperados.get(i);
            if (t.getPeriodo() == null || !t.getPeriodo().equals(e.getPeriodo())) {
                throw new NegocioException("El desglose mensual del descanso es inválido.");
            }
            if (!t.getFechaDesde().equals(e.getFechaDesde())
                    || !t.getFechaHasta().equals(e.getFechaHasta())
                    || !t.getDiasSubsidio().equals(e.getDiasSubsidio())) {
                throw new NegocioException("El desglose mensual del descanso es inválido.");
            }
            if (!periodos.add(t.getPeriodo())) {
                throw new NegocioException("El desglose mensual contiene periodos duplicados.");
            }
            if (i > 0) {
                long gap = ChronoUnit.DAYS.between(
                        tramos.get(i - 1).getFechaHasta(), t.getFechaDesde());
                if (gap != 1) {
                    throw new NegocioException("El desglose mensual presenta huecos entre tramos.");
                }
            }
        }
    }
}
