package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.CatalogoNombreRequest;
import com.indeci.rrhh.dto.UbigeoDto;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.BankAccountType;
import com.indeci.rrhh.service.CatalogoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping("/ubigeo")
    public ApiResponse<List<UbigeoDto>> ubigeo() {
        return new ApiResponse<>("OK", "Ubigeo completo",
                catalogoService.listarUbigeo());
    }

    @GetMapping("/bancos")
    public ApiResponse<List<Bank>> bancos() {
        return new ApiResponse<>("OK", "Lista de bancos",
                catalogoService.listarBancos());
    }

    @GetMapping("/tipos-cuenta")
    public ApiResponse<List<BankAccountType>> tiposCuenta() {
        return new ApiResponse<>("OK", "Tipos de cuenta",
                catalogoService.listarTiposCuenta());
    }

    @PostMapping("/bancos")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Bank> crearBanco(@Valid @RequestBody CatalogoNombreRequest body) {
        Bank saved = catalogoService.crearBanco(body);
        return new ApiResponse<>("OK", "Banco registrado", saved);
    }

    @PutMapping("/bancos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Bank> actualizarBanco(
            @PathVariable Long id,
            @Valid @RequestBody CatalogoNombreRequest body) {
        Bank saved = catalogoService.actualizarBanco(id, body);
        return new ApiResponse<>("OK", "Banco actualizado", saved);
    }

    @DeleteMapping("/bancos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> eliminarBanco(@PathVariable Long id) {
        catalogoService.eliminarBanco(id);
        return new ApiResponse<>("OK", "Banco dado de baja", null);
    }

    @PostMapping("/tipos-cuenta")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BankAccountType> crearTipoCuenta(@Valid @RequestBody CatalogoNombreRequest body) {
        BankAccountType saved = catalogoService.crearTipoCuenta(body);
        return new ApiResponse<>("OK", "Tipo de cuenta registrado", saved);
    }

    @PutMapping("/tipos-cuenta/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BankAccountType> actualizarTipoCuenta(
            @PathVariable Long id,
            @Valid @RequestBody CatalogoNombreRequest body) {
        BankAccountType saved = catalogoService.actualizarTipoCuenta(id, body);
        return new ApiResponse<>("OK", "Tipo de cuenta actualizado", saved);
    }

    @DeleteMapping("/tipos-cuenta/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> eliminarTipoCuenta(@PathVariable Long id) {
        catalogoService.eliminarTipoCuenta(id);
        return new ApiResponse<>("OK", "Tipo de cuenta dado de baja", null);
    }
}
