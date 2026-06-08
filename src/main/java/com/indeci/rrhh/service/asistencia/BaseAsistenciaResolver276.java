package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Order(20)
public class BaseAsistenciaResolver276 implements BaseAsistenciaStrategy {

    static final String ORIGEN = "RESOLVER_276_MUC_PENDIENTE";

    @Override
    public boolean soporta(String codigoRegimen) {
        return codigoRegimen != null
                && codigoRegimen.toUpperCase(Locale.ROOT).contains("276");
    }

    @Override
    public BaseAsistenciaResult resolver(EmpleadoPlanilla planilla, String codigoRegimen) {
        BaseAsistenciaResult result =
                BaseAsistenciaSupport.desdeSueldoBasico(planilla, ORIGEN);
        BaseAsistenciaSupport.agregarAdvertenciaProvisional(result);
        return result;
    }
}
