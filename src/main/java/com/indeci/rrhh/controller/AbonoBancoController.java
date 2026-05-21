package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.AbonoBancoDto;
import com.indeci.rrhh.dto.AbonoBancoResponseDto;
import com.indeci.rrhh.dto.ResumenBancoDto;
import com.indeci.rrhh.dto.TicketMcppDto;
import com.indeci.rrhh.dto.TicketMcppMasivoDto;
import com.indeci.rrhh.service.AbonoBancoService;

import lombok.RequiredArgsConstructor;

/**
 * Spec 010 / M14 — Abonos bancarios + ticket MCPP (SPEC §12.2 PANTALLA-07).
 */
@RestController
@RequestMapping("/api/rrhh/abono-banco")
@RequiredArgsConstructor
public class AbonoBancoController {

    private final AbonoBancoService service;

    @PostMapping
    public ApiResponse<Void> registrar(@RequestBody AbonoBancoDto dto) {
        service.registrar(dto);
        return new ApiResponse<>("OK", "Abono bancario registrado", null);
    }

    /** Genera (UPSERT) los abonos de todos los movimientos del período. */
    @PostMapping("/generar/{periodo}")
    public ApiResponse<Integer> generarAbonosPeriodo(
            @PathVariable String periodo) {
        int afectados = service.generarAbonosPeriodo(periodo);
        return new ApiResponse<>("OK",
                "Abonos generados: " + afectados, afectados);
    }

    /** Resumen de abonos del período agrupado por banco. */
    @GetMapping("/resumen-banco/{periodo}")
    public ApiResponse<List<ResumenBancoDto>> resumenPorBanco(
            @PathVariable String periodo) {
        return new ApiResponse<>("OK", "Resumen por banco",
                service.resumenPorBanco(periodo));
    }

    @GetMapping("/movimiento/{movimientoPlanillaId}")
    public ApiResponse<List<AbonoBancoResponseDto>> listarPorMovimiento(
            @PathVariable Long movimientoPlanillaId) {
        return new ApiResponse<>("OK", "Abonos del movimiento",
                service.listarPorMovimiento(movimientoPlanillaId));
    }

    @PutMapping("/{id}/ticket")
    public ApiResponse<Void> registrarTicketMcpp(
            @PathVariable Long id, @RequestBody TicketMcppDto dto) {
        service.registrarTicketMcpp(id, dto);
        return new ApiResponse<>("OK", "Ticket MCPP registrado — abono procesado", null);
    }

    /** Aplica un mismo ticket MCPP a varios abonos a la vez. */
    @PutMapping("/ticket-masivo")
    public ApiResponse<Integer> registrarTicketMcppMasivo(
            @RequestBody TicketMcppMasivoDto dto) {
        int procesados = service.registrarTicketMcppMasivo(dto);
        return new ApiResponse<>("OK",
                "Ticket MCPP aplicado a " + procesados + " abono(s)", procesados);
    }
}
