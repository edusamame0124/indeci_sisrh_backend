package com.indeci.rrhh.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.SaldoVacacionalDto;
import com.indeci.rrhh.dto.VacacionDto;
import com.indeci.rrhh.dto.VacacionResponseDto;
import com.indeci.rrhh.service.VacacionService;
import com.indeci.security.util.SecurityUtil;

import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rrhh/vacaciones")
public class VacacionController {
	
	private final VacacionService vacacionService;
	
	@PostMapping("/registrar")
	public ApiResponse<Void>
	registrar(
	        @RequestBody
	        VacacionDto dto) {

		vacacionService.registrar(dto);

	    return new ApiResponse<>(
	            "OK",
	            "Periodo vacacional registrado",
	            null);
	}
	
	@GetMapping("/empleado/{empleadoId}")
	public ApiResponse<
	        List<VacacionResponseDto>>
	listarEmpleado(
	        @PathVariable Long empleadoId) {

	    return new ApiResponse<>(
	            "OK",
	            "Listado correcto",
	            vacacionService.listarPorEmpleado(
	                    empleadoId));
	}
	
	@GetMapping("/mis-periodos")
	public ApiResponse<
	        List<VacacionResponseDto>>
	misPeriodos() {

	    Long empleadoId =
	            SecurityUtil.getEmpleadoId();

	    return new ApiResponse<>(
	            "OK",
	            "Mis periodos vacacionales",
	            vacacionService.listarPorEmpleado(
	                    empleadoId));
	}
	
	@GetMapping("/saldo")
	public ApiResponse<SaldoVacacionalDto>
	saldo() {

	    Long empleadoId =
	            SecurityUtil.getEmpleadoId();

	    return new ApiResponse<>(
	            "OK",
	            "Saldo vacacional",
	            vacacionService.obtenerSaldoVacacional(
	                    empleadoId));
	}
	
	

}
