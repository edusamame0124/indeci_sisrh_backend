package com.indeci.rrhh.service;

import lombok.RequiredArgsConstructor;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
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



private FTPClient conectar()
        throws Exception {

    FTPClient ftp =
            new FTPClient();

    ftp.connect(
            host,
            port);

    ftp.login(
            user,
            password);

    ftp.enterLocalPassiveMode();

    ftp.setFileType(
            FTP.BINARY_FILE_TYPE);

    return ftp;
}


public String subirArchivo(
        MultipartFile file,
        String carpeta,
        String nombreArchivo) {

    FTPClient ftp = null;

    try {

        ftp = conectar();

        String rutaCompleta =
                basePath
                        + carpeta;

        crearDirectorios(
                ftp,
                rutaCompleta);

        ftp.changeWorkingDirectory(
                rutaCompleta);

        try (InputStream input =
                     file.getInputStream()) {

            boolean ok =
                    ftp.storeFile(
                            nombreArchivo,
                            input);

            if (!ok) {

                throw new RuntimeException(
                        "No se pudo subir archivo FTP");
            }
        }

        return rutaCompleta
                + "/"
                + nombreArchivo;

    } catch (Exception e) {

        throw new RuntimeException(
                "Error FTP: "
                        + e.getMessage());

    } finally {

        try {

            if (ftp != null
                    && ftp.isConnected()) {

                ftp.logout();
                ftp.disconnect();
            }

        } catch (Exception ex) {

            ex.printStackTrace();
        }
    }
}

private void crearDirectorios(
        FTPClient ftp,
        String path)
        throws Exception {

    String[] dirs =
            path.split("/");

    String ruta = "";

    for (String dir : dirs) {

        if (dir == null
                || dir.isBlank()) {

            continue;
        }

        ruta += "/" + dir;

        ftp.makeDirectory(ruta);
    }
}
}