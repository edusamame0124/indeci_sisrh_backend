package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Almacenamiento de legajo: FTP institucional o disco local (fallback dev).
 */
@Service
@Slf4j
public class FtpService {

    @Value("${ftp.host}")
    private String host;

    @Value("${ftp.port}")
    private Integer port;

    @Value("${ftp.user}")
    private String user;

    @Value("${ftp.password}")
    private String password;

    @Value("${ftp.base-path}")
    private String basePath;

    @Value("${ftp.local-fallback-enabled:true}")
    private boolean localFallbackEnabled;

    @Value("${ftp.local-base-path:./data/legajo}")
    private String localBasePath;

    public String subirArchivo(
            MultipartFile file,
            String carpeta,
            String nombreArchivo) {

        if (file.isEmpty()) {
            throw new NegocioException("El archivo adjunto está vacío.");
        }

        String nombreFinal =
                System.currentTimeMillis() + "_" + nombreArchivo;

        if (usarAlmacenamientoLocal()) {
            return subirArchivoLocal(file, carpeta, nombreFinal);
        }

        FTPClient ftp = null;

        try {
            ftp = conectar();

            String rutaCompleta = basePath + carpeta;
            crearDirectorios(ftp, rutaCompleta);
            ftp.changeWorkingDirectory(rutaCompleta);

            try (InputStream input = file.getInputStream()) {
                boolean ok = ftp.storeFile(nombreFinal, input);
                if (!ok) {
                    throw new NegocioException(
                            "No se pudo subir el documento al servidor de archivos.");
                }
            }

            return rutaCompleta + "/" + nombreFinal;

        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error FTP al subir legajo", e);
            throw new NegocioException(
                    "No se pudo guardar el sustento documental. "
                            + "Verifique la conexión FTP o use almacenamiento local en desarrollo.");
        } finally {
            desconectar(ftp);
        }
    }
    
    public String subirPdfGenerado(
            byte[] contenido,
            String carpeta,
            String nombreArchivo) {

        String nombreFinal =
                System.currentTimeMillis() + "_" + nombreArchivo;

        if (usarAlmacenamientoLocal()) {

            try {

                Path dir =
                        Paths.get(
                                localBasePath,
                                carpeta.split("/"));

                Files.createDirectories(dir);

                Path destino =
                        dir.resolve(nombreFinal);

                Files.write(
                        destino,
                        contenido);

                return destino
                        .toAbsolutePath()
                        .toString()
                        .replace('\\', '/');

            } catch (Exception e) {

                throw new NegocioException(
                        "No se pudo guardar el archivo");
            }
        }

        FTPClient ftp = null;

        try {

            ftp = conectar();

            String rutaCompleta =
                    basePath + carpeta;

            crearDirectorios(
                    ftp,
                    rutaCompleta);

            ftp.changeWorkingDirectory(
                    rutaCompleta);

            try (java.io.ByteArrayInputStream input =
                         new java.io.ByteArrayInputStream(
                                 contenido)) {

                boolean ok =
                        ftp.storeFile(
                                nombreFinal,
                                input);

                if (!ok) {

                    throw new NegocioException(
                            "No se pudo subir el documento");
                }
            }

            return rutaCompleta
                    + "/"
                    + nombreFinal;

        } catch (Exception e) {

            throw new NegocioException(
                    "No se pudo guardar el documento");

        } finally {

            desconectar(ftp);
        }
    }

    public byte[] descargarArchivo(String rutaArchivo) {
        if (usarAlmacenamientoLocal()) {
            return descargarArchivoLocal(rutaArchivo);
        }

        FTPClient ftp = null;

        try {
            ftp = conectar();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean ok = ftp.retrieveFile(rutaArchivo, output);

            if (!ok) {
                throw new NegocioException(
                        "No se encontró el documento en el servidor de archivos.");
            }

            return output.toByteArray();

        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error FTP al descargar legajo: {}", rutaArchivo, e);
            throw new NegocioException(
                    "No se pudo descargar el sustento documental.");
        } finally {
            desconectar(ftp);
        }
    }

    private boolean usarAlmacenamientoLocal() {
        return localFallbackEnabled
                && (user == null || user.isBlank());
    }

    private String subirArchivoLocal(
            MultipartFile file,
            String carpeta,
            String nombreFinal) {
        try {
            Path dir = Paths.get(localBasePath, carpeta.split("/"));
            Files.createDirectories(dir);
            Path destino = dir.resolve(nombreFinal);
            file.transferTo(destino);

            String ruta = destino.toAbsolutePath().toString().replace('\\', '/');
            log.info("Legajo guardado en almacenamiento local: {}", ruta);
            return ruta;

        } catch (Exception e) {
            log.error("Error almacenamiento local legajo", e);
            throw new NegocioException(
                    "No se pudo guardar el sustento documental en disco local.");
        }
    }

    private byte[] descargarArchivoLocal(String rutaArchivo) {
        try {
            Path path = Paths.get(rutaArchivo);
            if (!Files.exists(path)) {
                Path relativa = Paths.get(localBasePath).resolve(rutaArchivo);
                path = Files.exists(relativa) ? relativa : path;
            }
            if (!Files.exists(path)) {
                throw new NegocioException(
                        "No se encontró el documento en almacenamiento local.");
            }
            return Files.readAllBytes(path);
        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error descarga local legajo: {}", rutaArchivo, e);
            throw new NegocioException(
                    "No se pudo leer el sustento documental desde disco local.");
        }
    }

    private FTPClient conectar() throws Exception {
        FTPClient ftp = new FTPClient();
        ftp.connect(host, port);
        log.debug("FTP reply: {}", ftp.getReplyString());

        boolean login = ftp.login(user, password);
        if (!login) {
            throw new NegocioException(
                    "No se pudo autenticar en el servidor FTP. "
                            + "Configure INDECI_FTP_USER e INDECI_FTP_PASSWORD.");
        }

        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        return ftp;
    }

    private void crearDirectorios(FTPClient ftp, String path) throws Exception {
        String[] dirs = path.split("/");
        String ruta = "";

        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            ruta += "/" + dir;
            ftp.makeDirectory(ruta);
        }
    }

    private void desconectar(FTPClient ftp) {
        if (ftp == null) {
            return;
        }
        try {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
        } catch (Exception ex) {
            log.warn("Error al cerrar sesión FTP", ex);
        }
    }
}
