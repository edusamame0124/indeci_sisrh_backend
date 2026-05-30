package com.indeci.rrhh.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.rrhh.service.FtpService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/ftp")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.RPT_READ)
public class FtpController {

    private final FtpService ftpService;

    @PostMapping("/upload")
    @PreAuthorize(SisrhSecurityExpressions.RPT_WRITE)
    public ResponseEntity<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("carpeta") String carpeta,
            @RequestParam("nombreArchivo") String nombreArchivo) {

        String ruta = ftpService.subirArchivo(file, carpeta, nombreArchivo);
        return ResponseEntity.ok(ruta);
    }
}
