package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Order(10)
public class BaseAsistenciaResolverCas implements BaseAsistenciaStrategy {

    static final String ORIGEN = "RESOLVER_CAS_SUELDO_CONCEPTOS_FIJOS";

    @Override
    public boolean soporta(String codigoRegimen) {
        if (codigoRegimen == null) {
            return false;
        }
        String codigo = codigoRegimen.toUpperCase(Locale.ROOT);
        return codigo.contains("CAS") || codigo.contains("1057");
    }

    @Override
    public BaseAsistenciaResult resolver(EmpleadoPlanilla planilla, String codigoRegimen) {
        BaseAsistenciaResult result = new BaseAsistenciaResult();
        result.setRemuneracionBase(
                BaseAsistenciaSupport.monto(planilla.getSueldoBasico())
                        + BaseAsistenciaSupport.monto(planilla.getMovilidad())
                        + BaseAsistenciaSupport.monto(planilla.getAlimentacion()));
        result.setOrigen(ORIGEN);
        BaseAsistenciaSupport.agregarAdvertenciaSueldoSiAplica(result, planilla);
        return result;
    }
}
