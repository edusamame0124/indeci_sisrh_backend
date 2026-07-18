package com.indeci.rrhh.vinculacion.importacion;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Un catálogo resoluble desde el texto del Excel.
 *
 * <p>El Excel trae <b>nombres</b> ({@code "CONTADOR PUBLICO"}), no ids. Este componente
 * traduce nombre → id construyendo un índice por clave normalizada.
 *
 * <p><b>La distinción que importa</b> es {@link Politica}:
 * <ul>
 *   <li>{@link Politica#ABIERTO} — catálogos que la entidad inventa (profesión, cargo,
 *       oficina, sede...). Si el valor no existe, <b>se da de alta</b> y la migración no se
 *       detiene. Se marca como advertencia para depurar después.</li>
 *   <li>{@link Politica#CERRADO} — catálogos oficiales (Ubigeo, AFP, régimen laboral y
 *       pensionario). <b>Nunca se inventan</b>: un distrito o una AFP falsos corromperían
 *       datos normativos. Si no hay match, se reporta como error.</li>
 * </ul>
 *
 * <p>El índice se carga una vez y se mantiene en memoria durante la importación (evita
 * una consulta por fila: con 617 filas × 13 catálogos serían ~8000 consultas).
 */
public class CatalogoTexto {

    public enum Politica {
        ABIERTO,
        CERRADO
    }

    /**
     * Resultado de resolver un texto.
     *
     * @param id       id del catálogo, o {@code null} si no se pudo resolver
     * @param dadoDeAlta {@code true} si hubo que crear la entrada
     */
    public record Resolucion(Long id, boolean dadoDeAlta) {
        public boolean resuelto() {
            return id != null;
        }

        /**
         * Entrega el id al setter solo si se resolvió. Evita repetir el chequeo de null en
         * cada asignación del upsert: {@code sesion.cargo(txt).id(puesto::setCargoId)}.
         */
        public void id(java.util.function.Consumer<Long> setter) {
            if (id != null) {
                setter.accept(id);
            }
        }

        static Resolucion no() {
            return new Resolucion(null, false);
        }
    }

    private final String nombre;
    private final Politica politica;
    private final Supplier<List<Object[]>> cargarPares;
    private final Function<String, Long> alta;

    private Map<String, Long> indice;

    /**
     * @param nombre      nombre legible del catálogo (para mensajes)
     * @param politica    si admite alta automática
     * @param cargarPares provee pares {@code [nombre, id]} de todas las entradas vigentes
     * @param alta        crea una entrada y devuelve su id; {@code null} si {@link Politica#CERRADO}
     */
    public CatalogoTexto(String nombre, Politica politica,
            Supplier<List<Object[]>> cargarPares, Function<String, Long> alta) {
        this.nombre = nombre;
        this.politica = politica;
        this.cargarPares = cargarPares;
        this.alta = alta;
    }

    public String getNombre() {
        return nombre;
    }

    public Politica getPolitica() {
        return politica;
    }

    /**
     * Resuelve un texto ya canonizado a su id.
     *
     * @param textoCanonico valor del Excel (idealmente pasado por {@link DiccionarioEquivalencias})
     * @return la resolución; {@link Resolucion#resuelto()} es {@code false} si no hay match
     *         y el catálogo es {@link Politica#CERRADO}.
     */
    public Resolucion resolver(String textoCanonico) {
        final String clave = TextoNormalizador.clave(textoCanonico);
        if (clave == null) {
            return Resolucion.no();
        }
        final Long existente = indice().get(clave);
        if (existente != null) {
            return new Resolucion(existente, false);
        }
        if (politica == Politica.CERRADO || alta == null) {
            return Resolucion.no();
        }
        final Long nuevoId = alta.apply(TextoNormalizador.limpiar(textoCanonico));
        indice().put(clave, nuevoId);
        return new Resolucion(nuevoId, true);
    }

    /**
     * Índice clave→id, construido una sola vez.
     *
     * <p>Ante entradas duplicadas gana el <b>id más bajo</b> (p. ej. INDECI_REGIMEN_PENSIONARIO
     * tiene 'INTEGRA' con ids 1 y 22). Sin este orden la resolución dependería del orden que
     * devuelva la BD y podría variar entre ejecuciones.
     */
    private Map<String, Long> indice() {
        if (indice == null) {
            indice = new HashMap<>();
            cargarPares.get().stream()
                    .filter(par -> par[0] != null && par[1] != null)
                    .sorted(Comparator.comparingLong(par -> (Long) par[1]))
                    .forEach(par -> {
                        final String clave = TextoNormalizador.clave((String) par[0]);
                        if (clave != null) {
                            indice.putIfAbsent(clave, (Long) par[1]);
                        }
                    });
        }
        return indice;
    }
}
