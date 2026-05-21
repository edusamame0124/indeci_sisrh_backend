package com.indeci.rrhh.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.entity.AbonoBanco;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.AbonoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 013 / C1 · P-07 — Archivo bancario TXT por banco (SPEC §12.2 PANTALLA-07).
 *
 * <p>Genera un ZIP con un {@code .txt} por banco para la carga en banca
 * electrónica. Cada línea son 10 campos separados por tabulación
 * (hoja «4.RES. BANCO» del Excel de planilla):
 *
 * <pre>NRO_ORDEN  BANCO  NRO_CUENTA  CCI  DNI  APELLIDOS_NOMBRES  META
 * REGISTRO_AIRHSP  MONTO_NETO  NRO_TICKET_MCPP</pre>
 *
 * <p>Solo lee datos ya calculados (abonos del período); no toca el motor de
 * planilla ni la generación de abonos.
 */
@Service
@RequiredArgsConstructor
public class ArchivoBancoService {

    /** Separador de campos del layout (tabulación). */
    private static final char TAB = '\t';

    /** Fin de línea esperado por la banca electrónica (CRLF). */
    private static final String EOL = "\r\n";

    /** "Bancos" sintéticos de un abono sin cuenta — no van al archivo. */
    private static final Set<String> NO_BANCOS = Set.of("SIN CUENTA", "SIN BANCO");

    private final MovimientoPlanillaRepository movimientoRepository;
    private final AbonoBancoRepository abonoRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final PersonaService personaService;

    /**
     * Construye el ZIP con un archivo de abonos por banco para el período.
     *
     * @throws NegocioException si el período no tiene abonos con cuenta bancaria.
     */
    public byte[] generarZip(String periodo) {

        // 1) Abonos del período (localizados vía sus movimientos).
        List<AbonoBanco> abonos = new ArrayList<>();
        for (MovimientoPlanilla mov :
                movimientoRepository.findByPeriodoAndActivo(periodo, 1)) {
            abonos.addAll(abonoRepository.findByMovimientoPlanillaId(mov.getId()));
        }
        if (abonos.isEmpty()) {
            throw new NegocioException(
                    "El período " + periodo + " no tiene abonos generados");
        }

        // 2) Identidad del empleado (DNI / nombres) y registro AIRHSP.
        Map<Long, PersonaEmpleadoResponseDto> personas = new HashMap<>();
        for (PersonaEmpleadoResponseDto p : personaService.listar()) {
            if (p.getEmpleadoId() != null) {
                personas.put(p.getEmpleadoId(), p);
            }
        }

        // 3) Agrupar por banco (descartando los sintéticos sin cuenta).
        Map<String, List<AbonoBanco>> porBanco = new TreeMap<>();
        for (AbonoBanco abono : abonos) {
            String banco = abono.getBanco();
            if (banco == null || NO_BANCOS.contains(banco.toUpperCase(Locale.ROOT))) {
                continue;
            }
            porBanco.computeIfAbsent(banco, b -> new ArrayList<>()).add(abono);
        }
        if (porBanco.isEmpty()) {
            throw new NegocioException(
                    "El período " + periodo + " no tiene abonos con cuenta bancaria");
        }

        // 4) Un .txt por banco dentro del ZIP.
        String yyyymm = periodo.replace("-", "");
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (Map.Entry<String, List<AbonoBanco>> e : porBanco.entrySet()) {
                String nombre = "ABONO_" + sanitizar(e.getKey()) + "_" + yyyymm + ".txt";
                zip.putNextEntry(new ZipEntry(nombre));
                zip.write(construirTxt(e.getKey(), e.getValue(), personas)
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (Exception ex) {
            throw new NegocioException(
                    "No se pudo generar el archivo bancario: " + ex.getMessage());
        }
        return salida.toByteArray();
    }

    // ============================ HELPERS ============================

    /** Arma el TXT de un banco: una línea de 10 campos por abono. */
    private String construirTxt(
            String banco,
            List<AbonoBanco> abonos,
            Map<Long, PersonaEmpleadoResponseDto> personas) {

        abonos.sort(Comparator.comparing(
                a -> nombre(personas.get(a.getEmpleadoId())),
                String.CASE_INSENSITIVE_ORDER));

        StringBuilder sb = new StringBuilder();
        int orden = 0;
        for (AbonoBanco a : abonos) {
            PersonaEmpleadoResponseDto persona = personas.get(a.getEmpleadoId());
            orden++;
            sb.append(orden).append(TAB)
              .append(banco).append(TAB)
              .append(nz(a.getNroCuenta())).append(TAB)
              .append(nz(a.getCci())).append(TAB)
              .append(persona != null ? nz(persona.getDni()) : "").append(TAB)
              .append(nombre(persona)).append(TAB)
              .append(nz(a.getMeta())).append(TAB)
              .append(registroAirhsp(a.getEmpleadoId())).append(TAB)
              .append(monto(a.getMontoNeto())).append(TAB)
              .append(nz(a.getNroTicketMcpp()))
              .append(EOL);
        }
        return sb.toString();
    }

    /** Código MEF / registro AIRHSP del empleado en su configuración de planilla. */
    private String registroAirhsp(Long empleadoId) {
        return planillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(p -> nz(p.getCodigoAirhsp()))
                .orElse("");
    }

    private static String nombre(PersonaEmpleadoResponseDto p) {
        return p != null && p.getNombreCompleto() != null
                ? p.getNombreCompleto() : "";
    }

    private static String monto(Double valor) {
        return String.format(Locale.US, "%.2f", valor != null ? valor : 0d);
    }

    private static String nz(String value) {
        return value != null ? value : "";
    }

    /** Deja el nombre del banco apto para nombre de archivo. */
    private static String sanitizar(String banco) {
        return banco.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
