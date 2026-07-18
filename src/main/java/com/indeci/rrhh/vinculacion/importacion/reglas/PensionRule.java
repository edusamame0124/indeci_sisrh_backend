package com.indeci.rrhh.vinculacion.importacion.reglas;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.indeci.rrhh.vinculacion.importacion.PensionExcelParser;
import com.indeci.rrhh.vinculacion.importacion.PensionExcelParser.PensionLeida;
import com.indeci.rrhh.vinculacion.importacion.ReglaVinculacion;
import com.indeci.rrhh.vinculacion.importacion.RowIssue;
import com.indeci.rrhh.vinculacion.importacion.VinculacionColumna;
import com.indeci.rrhh.vinculacion.importacion.VinculacionRowRaw;

/**
 * Régimen pensionario. Valida lo que {@link PensionExcelParser} logró interpretar de
 * las columnas "Sistema pensionario" y "AFP", que en el Excel vienen mezcladas.
 *
 * <p>Si el afiliado es AFP se exige CUSPP (12 caracteres). Caso real conocido:
 * fila 375 trae {@code '33030LGVLA7'} (11) — se reporta como ERROR porque no se puede
 * adivinar el carácter faltante.
 */
@Component
public class PensionRule implements ReglaVinculacion {

    private static final int CUSPP_LONGITUD = 12;

    private final PensionExcelParser parser;

    public PensionRule(PensionExcelParser parser) {
        this.parser = parser;
    }

    @Override
    public int orden() {
        return 50;
    }

    @Override
    public List<RowIssue> validar(VinculacionRowRaw fila) {
        final List<RowIssue> issues = new ArrayList<>();
        final PensionLeida pension = parser.parsear(fila);

        if (pension.sistema() == null) {
            issues.add(RowIssue.error(VinculacionColumna.SISTEMA_PENSIONARIO,
                    "Sistema pensionario vacío o no reconocido. Válidos: ONP, AFP, CPMP, Ley 20530."));
            return issues;
        }

        if (pension.condicionEspecialAfp() != null) {
            issues.add(RowIssue.saneado(VinculacionColumna.AFP,
                    "Condición especial separada de la AFP: '" + pension.condicionEspecialAfp() + "'."));
        }

        if (!pension.esAfp()) {
            return issues;
        }

        if (pension.afp() == null) {
            issues.add(RowIssue.error(VinculacionColumna.AFP,
                    "Sistema AFP sin AFP identificada (Integra, Prima, Profuturo, Habitat)."));
        }

        final String cuspp = fila.texto(VinculacionColumna.CUSPP);
        if (cuspp == null) {
            issues.add(RowIssue.advertencia(VinculacionColumna.CUSPP,
                    "Afiliado AFP sin CUSPP."));
        } else if (cuspp.length() != CUSPP_LONGITUD) {
            issues.add(RowIssue.error(VinculacionColumna.CUSPP,
                    "CUSPP de " + cuspp.length() + " caracteres: se esperan " + CUSPP_LONGITUD
                            + " (valor: '" + cuspp + "'). No se puede completar automáticamente."));
        }
        return issues;
    }
}
