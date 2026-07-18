package com.indeci.rrhh.vinculacion.importacion.reglas;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.indeci.rrhh.vinculacion.importacion.ReglaVinculacion;
import com.indeci.rrhh.vinculacion.importacion.RowIssue;
import com.indeci.rrhh.vinculacion.importacion.VinculacionColumna;
import com.indeci.rrhh.vinculacion.importacion.VinculacionRowRaw;

/**
 * Identidad de la persona: DNI y nombre. El DNI es la llave con la que el import
 * encuentra o crea al empleado, así que sin él la fila no puede procesarse.
 */
@Component
public class IdentidadRule implements ReglaVinculacion {

    private static final int DNI_LONGITUD = 8;

    @Override
    public int orden() {
        return 10;
    }

    @Override
    public List<RowIssue> validar(VinculacionRowRaw fila) {
        final List<RowIssue> issues = new ArrayList<>();

        final String dni = fila.digitos(VinculacionColumna.DNI);
        if (dni == null) {
            issues.add(RowIssue.error(VinculacionColumna.DNI, "DNI vacío: es la llave del empleado."));
        } else if (dni.length() > DNI_LONGITUD) {
            issues.add(RowIssue.error(VinculacionColumna.DNI,
                    "DNI de " + dni.length() + " dígitos: se esperan " + DNI_LONGITUD + " (valor: " + dni + ")."));
        } else if (dni.length() < DNI_LONGITUD) {
            // Excel pudo leerlo como número y comerse los ceros a la izquierda.
            issues.add(RowIssue.saneado(VinculacionColumna.DNI,
                    "DNI completado con ceros a la izquierda: " + dni + " → "
                            + com.indeci.rrhh.vinculacion.importacion.TextoNormalizador.padCeros(dni, DNI_LONGITUD)));
        }

        if (fila.texto(VinculacionColumna.NOMBRE_COMPLETO) == null) {
            issues.add(RowIssue.error(VinculacionColumna.NOMBRE_COMPLETO, "Apellidos y nombres vacío."));
        }
        return issues;
    }
}
