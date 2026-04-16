package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.UbigeoDto;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.BankAccountType;
import com.indeci.rrhh.service.CatalogoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    // 🔹 UBIGEO
    @GetMapping("/ubigeo")
    public ApiResponse<List<UbigeoDto>> ubigeo() {
        return new ApiResponse<>("OK", "Ubigeo completo",
                catalogoService.listarUbigeo());
    }

    // 🔹 BANCOS
    @GetMapping("/bancos")
    public ApiResponse<List<Bank>> bancos() {
        return new ApiResponse<>("OK", "Lista de bancos",
                catalogoService.listarBancos());
    }

    // 🔹 TIPOS DE CUENTA
    @GetMapping("/tipos-cuenta")
    public ApiResponse<List<BankAccountType>> tiposCuenta() {
        return new ApiResponse<>("OK", "Tipos de cuenta",
                catalogoService.listarTiposCuenta());
    }
}