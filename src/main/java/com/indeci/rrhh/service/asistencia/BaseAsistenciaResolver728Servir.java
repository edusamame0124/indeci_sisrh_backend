package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Order(30)
public class BaseAsistenciaResolver728Servir implements BaseAsistenciaStrategy {

    static final String ORIGEN = "RESOLVER_728_SERVIR_PENDIENTE";

    @Override
    public boolean soporta(String codigoRegimen) {
        if (codigoRegimen == null) {
            return false;
        }
        String codigo = codigoRegimen.toUpperCase(Locale.ROOT);
        return codigo.contains("728")
                || codigo.contains("SERVIR")
                || codigo.contains("30057");
    }

    @Override
    public BaseAsistenciaResult resolver(EmpleadoPlanilla planilla, String codigoRegimen) {
        BaseAsistenciaResult result =
                BaseAsistenciaSupport.desdeSueldoBasico(planilla, ORIGEN);
        BaseAsistenciaSupport.agregarAdvertenciaProvisional(result);
        return result;
    }
}
