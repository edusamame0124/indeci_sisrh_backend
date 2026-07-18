package com.indeci.rrhh.vinculacion.importacion;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Interpreta las columnas de pensión del Excel, que vienen <b>mezcladas</b>.
 *
 * <p>Realidad del archivo de RR.HH. (ver INFORME_VERIFICACION_EXCEL_OFICIAL.md):
 * <ul>
 *   <li>"Sistema pensionario" trae a veces el <i>nombre de la AFP</i>
 *       ({@code INTEGRA}, {@code PROFUTURO}) en lugar del sistema, y también
 *       {@code PENSIONISTA CPMP}.</li>
 *   <li>"AFP" trae ruido ({@code 0}, {@code NO}, {@code SI}) y la condición de retiro
 *       pegada al nombre: {@code 'INTEGRA - RETIRO 95.5%'}, {@code 'PRIMA  RETIRO 95.5%'}
 *       (con y sin guion, con doble espacio).</li>
 * </ul>
 *
 * <p>Este componente centraliza ese desorden para que reglas y mapper reciban ya
 * separados: sistema, AFP y condición especial (que en el modelo vive en
 * {@code EmpleadoPension.condicionEspecialAfp}).
 */
@Component
public class PensionExcelParser {

    /** Sistemas soportados. CPMP = Caja de Pensiones Militar-Policial (D.L. 19846 / D.Leg. 1133). */
    public static final String SISTEMA_ONP = "ONP";
    public static final String SISTEMA_AFP = "AFP";
    public static final String SISTEMA_CPMP = "CPMP";
    public static final String SISTEMA_20530 = "LEY_20530";

    private static final Set<String> AFPS = Set.of("INTEGRA", "PRIMA", "PROFUTURO", "HABITAT");
    /**
     * Código que exige el CHECK CHK_PENSION_CONDICION_AFP (V010_79): la columna solo acepta
     * NO_APLICA / RETIRO_955 / PENSIONISTA_SPP. El Excel escribe 'RETIRO 95.5%' como texto;
     * aquí se traduce al código válido.
     */
    private static final String RETIRO_95_5 = "RETIRO_955";
    /** Ruido conocido de la columna AFP que no identifica ninguna AFP. */
    private static final Set<String> RUIDO = Set.of("0", "NO", "SI", "-");

    /**
     * Resultado ya desagregado de las columnas de pensión.
     *
     * @param sistema               ONP | AFP | CPMP | LEY_20530; {@code null} si no se reconoce
     * @param afp                   nombre canónico de la AFP; {@code null} si no aplica
     * @param condicionEspecialAfp  p. ej. {@code "RETIRO 95.5%"}; {@code null} si no aplica
     */
    public record PensionLeida(String sistema, String afp, String condicionEspecialAfp) {
        public boolean esAfp() {
            return SISTEMA_AFP.equals(sistema);
        }
    }

    public PensionLeida parsear(VinculacionRowRaw fila) {
        final String sistemaCrudo = fila.clave(VinculacionColumna.SISTEMA_PENSIONARIO);
        final String afpCrudo = fila.clave(VinculacionColumna.AFP);

        final String condicion = detectarCondicion(sistemaCrudo, afpCrudo);
        // La AFP puede venir en cualquiera de las dos columnas.
        final String afp = primeraAfp(afpCrudo, sistemaCrudo);
        final String sistema = resolverSistema(sistemaCrudo, afp);

        return new PensionLeida(sistema, SISTEMA_AFP.equals(sistema) ? afp : null, condicion);
    }

    /** El sufijo de retiro puede venir pegado en cualquiera de las dos columnas. */
    private String detectarCondicion(String sistema, String afp) {
        return contieneRetiro(afp) || contieneRetiro(sistema) ? RETIRO_95_5 : null;
    }

    private boolean contieneRetiro(String valor) {
        return valor != null && valor.contains("RETIRO");
    }

    private String primeraAfp(String... valores) {
        for (String valor : valores) {
            final String afp = extraerAfp(valor);
            if (afp != null) {
                return afp;
            }
        }
        return null;
    }

    /** Busca el nombre de AFP dentro del texto, ignorando sufijos como '- RETIRO 95.5%'. */
    private String extraerAfp(String valor) {
        if (valor == null || RUIDO.contains(valor)) {
            return null;
        }
        return AFPS.stream().filter(valor::contains).findFirst().orElse(null);
    }

    private String resolverSistema(String sistemaCrudo, String afp) {
        if (sistemaCrudo == null) {
            // Sin sistema pero con AFP identificada → es AFP.
            return afp != null ? SISTEMA_AFP : null;
        }
        if (sistemaCrudo.contains("CPMP")) {
            return SISTEMA_CPMP;
        }
        if (sistemaCrudo.contains("20530")) {
            return SISTEMA_20530;
        }
        if (sistemaCrudo.contains("ONP")) {
            return SISTEMA_ONP;
        }
        // 'AFP' explícito, o el nombre de la AFP usado como sistema (INTEGRA/PROFUTURO...).
        if (sistemaCrudo.contains(SISTEMA_AFP) || afp != null) {
            return SISTEMA_AFP;
        }
        return null;
    }
}
