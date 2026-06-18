package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.TeletrabajoReporteCabeceraDto;
import com.indeci.rrhh.dto.TeletrabajoReporteDetDto;
import com.indeci.rrhh.dto.TeletrabajoReporteDto;
import com.indeci.rrhh.dto.TeletrabajoReporteResponseDto;
import com.indeci.rrhh.service.TeletrabajoReporteService;

import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/rrhh/teletrabajo")
@RequiredArgsConstructor

public class TeletrabajoReporteController {

    private final TeletrabajoReporteService
            service;

    @PostMapping
 
    public ApiResponse<Void>
    registrar(
            @RequestBody
            TeletrabajoReporteDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Reporte registrado",
                null);
    }

    @GetMapping
    public ApiResponse<
            List<TeletrabajoReporteResponseDto>>
    listar() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                service.listar());
    }

    @GetMapping("/{id}")
    public ApiResponse<
            TeletrabajoReporteResponseDto>
    obtener(
            @PathVariable Long id) {

        return new ApiResponse<>(
                "OK",
                "Detalle correcto",
                service.obtener(id));
    }

    @DeleteMapping("/{id}")

    public ApiResponse<Void>
    eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Eliminado correctamente",
                null);
    }
    
    @PostMapping("/detalle")
    public ApiResponse<Void>
    agregarDetalle(
            @RequestBody
            TeletrabajoReporteDetDto dto) {

        service.agregarDetalle(
                dto);

        return new ApiResponse<>(
                "OK",
                "Detalle registrado",
                null);
    }
    @PutMapping("/detalle/{id}")
    public ApiResponse<Void>
    actualizarDetalle(
            @PathVariable Long id,
            @RequestBody
            TeletrabajoReporteDetDto dto) {

        service.actualizarDetalle(
                id,
                dto);

        return new ApiResponse<>(
                "OK",
                "Detalle actualizado",
                null);
    }
    
    @DeleteMapping("/detalle/{id}")
    public ApiResponse<Void>
    eliminarDetalle(
            @PathVariable Long id) {

        service.eliminarDetalle(
                id);

        return new ApiResponse<>(
                "OK",
                "Detalle eliminado",
                null);
    }
    
    @PostMapping("/cabecera")
    public ApiResponse<Long>
    registrarCabecera(
            @RequestBody
            TeletrabajoReporteCabeceraDto dto) {

        Long id =
                service.registrarCabecera(
                        dto);

        return new ApiResponse<>(
                "OK",
                "Cabecera registrada",
                id);
    }
}