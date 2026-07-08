package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Punto único de lectura de archivos de marcador: detecta el formato y delega
 * al lector adecuado (Reloj 1 diario o Reloj 2 / COEN eventos), devolviendo la
 * misma estructura {@link AsistenciaCsvParser.ParseResult} para el resto del
 * pipeline. Habilita que ambos formatos convivan en el mismo flujo (SPEC D8).
 */
@Component
@RequiredArgsConstructor
public class AsistenciaLectorRouter {

    private final FormatoDetector detector;
    private final AsistenciaCsvParser parserDiario;
    private final AsistenciaEventosReader eventosReader;

    /** Resultado de lectura: formato detectado + filas normalizadas. */
    public record ResultadoLectura(
            FormatoMarcador formato,
            AsistenciaCsvParser.ParseResult parseResult) {
    }

    public ResultadoLectura leer(byte[] bytes) {
        FormatoMarcador formato = detector.detectar(bytes);
        return switch (formato) {
            case RELOJ1_DIARIO -> new ResultadoLectura(formato, parserDiario.parse(bytes));
            case RELOJ2_COEN -> new ResultadoLectura(formato, eventosReader.parse(bytes));
            case DESCONOCIDO -> throw new NegocioException(
                    "No se reconoce el formato del archivo. Debe ser el reporte del Reloj 1 "
                            + "(separado por ';' con DNI) o el reporte COEN / Reloj 2 "
                            + "(\"REPORTE DE MARCAS DEL PERSONAL\").");
        };
    }
}
