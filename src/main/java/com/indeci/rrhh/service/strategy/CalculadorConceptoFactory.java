package com.indeci.rrhh.service.strategy;

import com.indeci.exception.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CalculadorConceptoFactory {

    private final List<CalculadorConceptoStrategy> strategies;

    public CalculadorConceptoStrategy obtenerEstrategia(String codigoRegimen, String codigoConcepto) {
        return strategies.stream()
                .filter(s -> s.aplica(codigoRegimen, codigoConcepto))
                .findFirst()
                .orElseThrow(() -> new NegocioException(
                        "No existe una regla matemática soportada para el régimen '" + codigoRegimen + 
                        "' con el concepto '" + codigoConcepto + "'."));
    }
}
