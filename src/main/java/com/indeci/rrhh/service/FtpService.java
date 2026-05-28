package com.indeci.rrhh.service;

import lombok.RequiredArgsConstructor;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
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
    
    System.out.println(
            "FTP reply: "
                    + ftp.getReplyString());

    ftp.login(
            user,
            password);
    
    boolean login =
            ftp.login(
                    user,
                    password);
    
  

    if (!login) {

        throw new RuntimeException(
                "No se pudo autenticar en FTP");
    }
    
    System.out.println(
            "FTP login OK");

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
    
    if (file.isEmpty()) {

        throw new RuntimeException(
                "Archivo vacío");
    }
    
    String nombreFinal =
            System.currentTimeMillis()
            + "_"
            + nombreArchivo;

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
        	
        	
        	System.out.println(
        	        "Subiendo archivo...");

            boolean ok =
                    ftp.storeFile(
                    		nombreFinal,
                            input);

            if (!ok) {

                throw new RuntimeException(
                        "No se pudo subir archivo FTP");
            }
        }

        return rutaCompleta
                + "/"
                + nombreFinal;

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

public byte[] descargarArchivo(
        String rutaArchivo) {

    FTPClient ftp = null;

    try {

        ftp = conectar();

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        boolean ok =
                ftp.retrieveFile(
                        rutaArchivo,
                        output);

        if (!ok) {

            throw new RuntimeException(
                    "No se pudo descargar archivo FTP");
        }

        return output.toByteArray();

    } catch (Exception e) {

        throw new RuntimeException(
                "Error descargando FTP: "
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

}