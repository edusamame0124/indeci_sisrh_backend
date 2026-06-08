package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;

public interface BaseAsistenciaStrategy {

    boolean soporta(String codigoRegimen);

    BaseAsistenciaResult resolver(EmpleadoPlanilla planilla, String codigoRegimen);
}
