package com.indeci.rrhh.vinculacion.importacion.reglas;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.indeci.rrhh.service.GeneradorPlanillaService;
import com.indeci.rrhh.vinculacion.importacion.ReglaVinculacion;
import com.indeci.rrhh.vinculacion.importacion.RowIssue;
import com.indeci.rrhh.vinculacion.importacion.VinculacionColumna;
import com.indeci.rrhh.vinculacion.importacion.VinculacionRowRaw;

/**
 * El régimen laboral gobierna qué campos son obligatorios y cuáles no aplican.
 * Es la regla más crítica del import: el régimen decide 5ta/4ta categoría,
 * asignación familiar, topes y el concepto base MEF del motor.
 *
 * <ul>
 *   <li><b>CAS (1057)</b> → exige modalidad CAS; no lleva asignación familiar;
 *       no lleva grupo de servidor civil.</li>
 *   <li><b>SERVIR (30057)</b> → exige grupo de servidor civil válido. Sin él, el motor
 *       cae al fallback L003 ("Servidor Civil de Carrera") y clasificaría mal ante el
 *       MEF a funcionarios (L001) y directivos (L002).</li>
 * </ul>
 *
 * <p>Reutiliza {@link GeneradorPlanillaService#esRegimenCas} y
 * {@link GeneradorPlanillaService#esRegimenServir} para no duplicar el criterio de
 * alias (CAS≡1057, SERVIR≡30057) que ya usa el motor.
 */
@Component
public class RegimenGobiernaCamposRule implements ReglaVinculacion {

    /** Claves que reconoce el motor en SERVIR_SUBGRUPO_BASE_MEF. */
    private static final Set<String> GRUPOS_SERVIR = Set.of(
            "FUNCIONARIO", "DIRECTIVO", "CARRERA", "ACTIVIDADES_COMPLEMENTARIAS");

    @Override
    public int orden() {
        return 20;
    }

    @Override
    public List<RowIssue> validar(VinculacionRowRaw fila) {
        final List<RowIssue> issues = new ArrayList<>();
        final String regimen = fila.clave(VinculacionColumna.REGIMEN_LABORAL);

        if (regimen == null) {
            issues.add(RowIssue.error(VinculacionColumna.REGIMEN_LABORAL,
                    "Régimen laboral vacío: define impuestos, asignación familiar y topes del motor."));
            return issues;
        }

        final boolean esCas = GeneradorPlanillaService.esRegimenCas(regimen);
        final boolean esServir = GeneradorPlanillaService.esRegimenServir(regimen);

        if (!esCas && !esServir && !"276".equals(regimen) && !"728".equals(regimen)) {
            issues.add(RowIssue.error(VinculacionColumna.REGIMEN_LABORAL,
                    "Régimen no reconocido: '" + regimen + "'. Válidos: CAS/1057, 30057/SERVIR, 276, 728."));
            return issues;
        }

        if (esCas) {
            validarCas(fila, issues);
        }
        if (esServir) {
            validarServir(fila, issues);
        }
        return issues;
    }

    private void validarCas(VinculacionRowRaw fila, List<RowIssue> issues) {
        if (fila.texto(VinculacionColumna.MODALIDAD_CAS) == null) {
            issues.add(RowIssue.advertencia(VinculacionColumna.MODALIDAD_CAS,
                    "Régimen CAS sin modalidad (Necesidad Transitoria / Suplencia / Confianza)."));
        }
        if (fila.texto(VinculacionColumna.GRUPO_SERVIDOR_CIVIL) != null) {
            issues.add(RowIssue.advertencia(VinculacionColumna.GRUPO_SERVIDOR_CIVIL,
                    "El grupo de servidor civil es exclusivo de la Ley 30057; en CAS se ignora."));
        }
        if (Boolean.TRUE.equals(fila.logico(VinculacionColumna.TIENE_ASIGNACION_FAMILIAR))) {
            issues.add(RowIssue.error(VinculacionColumna.TIENE_ASIGNACION_FAMILIAR,
                    "El régimen CAS (1057) no percibe asignación familiar."));
        }
    }

    private void validarServir(VinculacionRowRaw fila, List<RowIssue> issues) {
        final String grupo = normalizarGrupo(fila.clave(VinculacionColumna.GRUPO_SERVIDOR_CIVIL));
        if (grupo == null) {
            issues.add(RowIssue.error(VinculacionColumna.GRUPO_SERVIDOR_CIVIL,
                    "Régimen SERVIR (30057) sin grupo de servidor civil. Requerido: "
                            + "FUNCIONARIO, DIRECTIVO, CARRERA o ACTIVIDADES_COMPLEMENTARIAS."));
        } else if (!GRUPOS_SERVIR.contains(grupo)) {
            issues.add(RowIssue.error(VinculacionColumna.GRUPO_SERVIDOR_CIVIL,
                    "Grupo de servidor civil no reconocido: '" + grupo + "'. El motor lo ignoraría y "
                            + "reportaría el sueldo como L003 (Servidor Civil de Carrera) ante el MEF."));
        }
    }

    /** Tolera 'ACTIVIDADES COMPLEMENTARIAS' y 'COMPLEMENTARIAS' como el mismo grupo. */
    private String normalizarGrupo(String grupo) {
        if (grupo == null) {
            return null;
        }
        final String g = grupo.replace(' ', '_');
        return "COMPLEMENTARIAS".equals(g) ? "ACTIVIDADES_COMPLEMENTARIAS" : g;
    }
}
