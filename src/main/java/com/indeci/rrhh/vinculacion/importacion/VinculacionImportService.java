package com.indeci.rrhh.vinculacion.importacion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.vinculacion.importacion.CatalogoTextResolver.Sesion;
import com.indeci.rrhh.vinculacion.importacion.VinculacionImportDtos.CommitDto;
import com.indeci.rrhh.vinculacion.importacion.VinculacionImportDtos.FilaPreviewDto;
import com.indeci.rrhh.vinculacion.importacion.VinculacionImportDtos.IssueDto;
import com.indeci.rrhh.vinculacion.importacion.VinculacionImportDtos.PreviewDto;

import lombok.RequiredArgsConstructor;

/**
 * Orquesta el import de vinculación en dos fases.
 *
 * <ol>
 *   <li>{@link #previsualizar} — lee y valida <b>sin escribir nada</b>. Devuelve el estado
 *       de cada fila para que RR.HH. corrija antes de confirmar.</li>
 *   <li>{@link #importar} — persiste solo las filas sin errores. Las demás se omiten y se
 *       reportan; una fila mala nunca bloquea a las buenas.</li>
 * </ol>
 *
 * <p>La previsualización obligatoria es la salvaguarda del proceso: el import toca 7 tablas
 * y alimenta al motor de planilla, así que nada se escribe a ciegas.
 */
@Service
@RequiredArgsConstructor
public class VinculacionImportService {

    private final VinculacionSheetReader reader;
    private final VinculacionRowValidator validator;
    private final VinculacionUpsertService upsertService;
    private final CatalogoTextResolver catalogoResolver;
    private final AuditoriaContext auditoriaContext;

    /** Lee y valida. No escribe: es seguro ejecutarlo cuantas veces haga falta. */
    public PreviewDto previsualizar(byte[] contenidoXlsx) {
        final List<VinculacionRowRaw> filas = reader.leer(contenidoXlsx);
        final List<FilaPreviewDto> detalle = filas.stream().map(this::aPreview).toList();

        return new PreviewDto(
                detalle.size(),
                (int) detalle.stream().filter(f -> !"ERROR".equals(f.estado())).count(),
                (int) detalle.stream().filter(f -> "ERROR".equals(f.estado())).count(),
                (int) detalle.stream().filter(f -> "ADVERTENCIA".equals(f.estado())).count(),
                detalle);
    }

    /**
     * Importa las filas válidas. Idempotente: la llave DNI + N.° de contrato hace que
     * re-subir el mismo archivo actualice en vez de duplicar.
     */
    @Auditable(accion = "IMPORTAR_VINCULACION")
    public CommitDto importar(byte[] contenidoXlsx) {
        final List<VinculacionRowRaw> filas = reader.leer(contenidoXlsx);
        final Sesion sesion = catalogoResolver.nuevaSesion();

        int creados = 0;
        int actualizados = 0;
        final List<FilaPreviewDto> omitidas = new ArrayList<>();
        // DNI → N.° de contrato declarados en el Excel (fuente de verdad para la sincronización).
        // Se registran las filas VÁLIDAS aunque luego fallen al persistir: un fallo transitorio no
        // debe hacer que un contrato declarado por RR.HH. se considere huérfano y se anule.
        final Map<String, Set<String>> contratosPorDni = new LinkedHashMap<>();

        for (VinculacionRowRaw fila : filas) {
            final VinculacionRowValidator.Resultado validacion = validator.validar(fila);
            if (validacion.tieneErrores()) {
                omitidas.add(aPreview(fila, validacion));
                continue;
            }
            registrarContratoDeclarado(fila, contratosPorDni);
            try {
                if (upsertService.importar(fila, sesion).creado()) {
                    creados++;
                } else {
                    actualizados++;
                }
            } catch (RuntimeException e) {
                // La fila corre en su propia transacción (REQUIRES_NEW): si falla al persistir,
                // solo esa se revierte. Se reporta como omitida y la carga continúa con las demás.
                omitidas.add(filaConErrorPersistencia(fila, e));
            }
        }

        final int anulados = sincronizarAutoritativo(contratosPorDni);

        auditoriaContext.setDetalle(String.format(
                "Import vinculación: %d filas; %d creados; %d actualizados; %d anulados (huérfanos); "
                        + "%d omitidos por error.",
                filas.size(), creados, actualizados, anulados, omitidas.size()));

        return new CommitDto(filas.size(), creados, actualizados, anulados, omitidas.size(), omitidas);
    }

    /** Registra el N.° de contrato declarado para el DNI de la fila (normalizado a 8 dígitos). */
    private void registrarContratoDeclarado(
            VinculacionRowRaw fila, Map<String, Set<String>> contratosPorDni) {
        final String dni = TextoNormalizador.padCeros(fila.digitos(VinculacionColumna.DNI), 8);
        final String contrato = fila.texto(VinculacionColumna.NUMERO_CONTRATO);
        if (dni != null && contrato != null) {
            contratosPorDni.computeIfAbsent(dni, k -> new HashSet<>()).add(contrato);
        }
    }

    /**
     * Cierra el import haciendo que el sistema sea espejo del Excel: por cada DNI importado, anula
     * los vínculos activos que el Excel ya no declara y reconcilia el vigente único. Cada empleado
     * se sincroniza en su propia transacción; un fallo aislado no tumba al resto.
     *
     * @return total de vínculos anulados por no figurar en el Excel
     */
    private int sincronizarAutoritativo(Map<String, Set<String>> contratosPorDni) {
        int anulados = 0;
        for (Map.Entry<String, Set<String>> e : contratosPorDni.entrySet()) {
            try {
                anulados += upsertService.sincronizar(e.getKey(), e.getValue()).anulados();
            } catch (RuntimeException ex) {
                // La sincronización de un empleado no puede tumbar la de los demás.
            }
        }
        return anulados;
    }

    /** Fila que pasó validación pero falló al persistir (p. ej. una restricción de BD). */
    private FilaPreviewDto filaConErrorPersistencia(VinculacionRowRaw fila, RuntimeException e) {
        final Throwable causaRaiz = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e);
        final String causa = causaRaiz.getMessage() != null
                ? causaRaiz.getMessage().split("\n")[0]
                : e.getClass().getSimpleName();
        return new FilaPreviewDto(
                fila.getNumeroFila(),
                fila.digitos(VinculacionColumna.DNI),
                fila.texto(VinculacionColumna.NOMBRE_COMPLETO),
                "ERROR",
                List.of(new IssueDto(
                        "Fila " + fila.getNumeroFila(), "—", "ERROR",
                        "No se pudo guardar: " + causa)));
    }

    private FilaPreviewDto aPreview(VinculacionRowRaw fila) {
        return aPreview(fila, validator.validar(fila));
    }

    private FilaPreviewDto aPreview(VinculacionRowRaw fila, VinculacionRowValidator.Resultado r) {
        return new FilaPreviewDto(
                fila.getNumeroFila(),
                fila.digitos(VinculacionColumna.DNI),
                fila.texto(VinculacionColumna.NOMBRE_COMPLETO),
                r.estado(),
                r.issues().stream().map(i -> IssueDto.de(i, fila.getNumeroFila())).toList());
    }
}
