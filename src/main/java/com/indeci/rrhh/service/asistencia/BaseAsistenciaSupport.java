package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;

final class BaseAsistenciaSupport {

    private BaseAsistenciaSupport() {
    }

    static double monto(Double value) {
        return value != null ? value : 0.0;
    }

    static BaseAsistenciaResult desdeSueldoBasico(EmpleadoPlanilla planilla, String origen) {
        BaseAsistenciaResult result = new BaseAsistenciaResult();
        result.setRemuneracionBase(monto(planilla.getSueldoBasico()));
        result.setOrigen(origen);
        agregarAdvertenciaSueldoSiAplica(result, planilla);
        return result;
    }

    static void agregarAdvertenciaSueldoSiAplica(
            BaseAsistenciaResult result,
            EmpleadoPlanilla planilla) {
        if (monto(planilla.getSueldoBasico()) <= 0) {
            result.getAdvertencias().add(
                    "Sueldo basico no configurado; revise planilla del empleado.");
        }
    }

    static void agregarAdvertenciaProvisional(BaseAsistenciaResult result) {
        result.getAdvertencias().add(
                "Base de asistencia con criterio provisional; confirmar con RR. HH.");
    }
}
