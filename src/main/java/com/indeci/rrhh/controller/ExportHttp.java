package com.indeci.rrhh.controller;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.indeci.exception.NegocioException;

/**
 * B3 — Helpers HTTP para descargas de archivos planos (PLAME/MCPP) y ZIP.
 * Charset ISO-8859-1: convención de los archivos planos del MEF/SUNAT.
 */
final class ExportHttp {

    private static final java.nio.charset.Charset CS = StandardCharsets.ISO_8859_1;

    private ExportHttp() {
    }

    /** Respuesta de archivo de texto plano descargable (attachment). */
    static ResponseEntity<byte[]> textoPlano(String nombre, String contenido) {
        byte[] bytes = contenido.getBytes(CS);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "plain", CS));
        headers.setContentDisposition(ContentDisposition.attachment().filename(nombre).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /** Respuesta ZIP con un archivo por entrada (nombre → contenido). */
    static ResponseEntity<byte[]> zip(String nombreZip, Map<String, String> archivos) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> e : archivos.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(CS));
                zos.closeEntry();
            }
        } catch (java.io.IOException ex) {
            throw new NegocioException("No se pudo construir el ZIP de exportación");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(nombreZip).build());
        return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
    }
}
