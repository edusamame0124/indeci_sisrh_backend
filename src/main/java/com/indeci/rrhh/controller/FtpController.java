package com.indeci.rrhh.controller;

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
    public ResponseEntity<String>
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

        return ResponseEntity.ok(
                ruta);
    }
}