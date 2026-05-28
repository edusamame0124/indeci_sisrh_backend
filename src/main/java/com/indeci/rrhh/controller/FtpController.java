package com.indeci.rrhh.controller;

import com.indeci.rrhh.dto.ArchivoResponseDto;
import com.indeci.rrhh.service.FtpService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rrhh/ftp")
@RequiredArgsConstructor
public class FtpController {

    private final FtpService
            ftpService;

    @PostMapping("/upload")
    public ResponseEntity<ArchivoResponseDto>
    upload(

            @RequestParam("file")
            MultipartFile file,

            @RequestParam("carpeta")
            String carpeta,

            @RequestParam("nombreArchivo")
            String nombreArchivo) {

    	String ruta =
    	        ftpService.subirArchivo(
    	                file,
    	                carpeta,
    	                nombreArchivo);

    	ArchivoResponseDto dto =
    	        new ArchivoResponseDto();

    	dto.setRutaArchivo(
    	        ruta);

    	dto.setNombreArchivo(
    	        nombreArchivo);

    	dto.setMimeType(
    	        file.getContentType());

    	dto.setTamanioBytes(
    	        file.getSize());

    	return ResponseEntity.ok(
    	        dto);
    }
    
    @GetMapping("/download")
    public ResponseEntity<byte[]>
    download(

            @RequestParam
            String rutaArchivo) {

        byte[] archivo =
                ftpService
                        .descargarArchivo(
                                rutaArchivo);

        return ResponseEntity
                .ok()
                .contentType(
                        org.springframework.http.MediaType.APPLICATION_PDF)
                .header(
                        "Content-Disposition",
                        "inline; filename=documento.pdf")
                .body(archivo);
    }
}