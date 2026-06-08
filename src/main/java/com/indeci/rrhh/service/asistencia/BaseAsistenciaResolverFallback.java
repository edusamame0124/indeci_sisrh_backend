package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class BaseAsistenciaResolverFallback implements BaseAsistenciaStrategy {

    static final String ORIGEN = "FALLBACK_SUELDO_BASICO";

    @Override
    public boolean soporta(String codigoRegimen) {
        return true;
    }

    @Override
    public BaseAsistenciaResult resolver(EmpleadoPlanilla planilla, String codigoRegimen) {
        BaseAsistenciaResult result =
                BaseAsistenciaSupport.desdeSueldoBasico(planilla, ORIGEN);
        BaseAsistenciaSupport.agregarAdvertenciaProvisional(result);
        return result;
    }
}
