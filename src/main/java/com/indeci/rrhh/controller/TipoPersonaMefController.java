package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.TipoPersonaMefDto;
import com.indeci.rrhh.service.TipoPersonaMefService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/tipo-persona-mef")
@RequiredArgsConstructor
public class TipoPersonaMefController {

    private final TipoPersonaMefService service;

    @GetMapping
    public ApiResponse<List<TipoPersonaMefDto>> listarActivos() {
        return new ApiResponse<>("OK", "Lista de tipos de persona MEF", service.listarActivos());
    }
}
