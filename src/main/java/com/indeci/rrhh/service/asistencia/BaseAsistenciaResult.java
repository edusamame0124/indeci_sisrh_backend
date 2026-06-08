package com.indeci.rrhh.service.asistencia;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BaseAsistenciaResult {

    private double remuneracionBase;
    private String origen;
    private final List<String> advertencias = new ArrayList<>();

    public static BaseAsistenciaResult vacio() {
        BaseAsistenciaResult r = new BaseAsistenciaResult();
        r.setRemuneracionBase(0.0);
        r.setOrigen("SIN_BASE");
        return r;
    }
}
