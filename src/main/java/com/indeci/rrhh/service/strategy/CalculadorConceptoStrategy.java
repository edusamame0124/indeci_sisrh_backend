package com.indeci.rrhh.service.strategy;

public interface CalculadorConceptoStrategy {
    boolean aplica(String codigoRegimen, String codigoConcepto);
    void ejecutarCalculo(ContextoCalculoPlanilla contexto);
}
