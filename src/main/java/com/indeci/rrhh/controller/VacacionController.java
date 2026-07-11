package com.indeci.rrhh.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ImportacionVacacionesResultDto;
import com.indeci.rrhh.dto.PadronVacacionalPageDto;
import com.indeci.rrhh.dto.PeriodoProgramadoDto;
import com.indeci.rrhh.dto.SaldoProporcionalDto;
import com.indeci.rrhh.dto.SaldoVacacionalDto;
import com.indeci.rrhh.dto.TiempoServicioDetalleDto;
import com.indeci.rrhh.dto.TiempoServicioDto;
import com.indeci.rrhh.dto.VacacionDto;
import com.indeci.rrhh.dto.VacacionResponseDto;
import com.indeci.rrhh.service.ImportadorVacacionesService;
import com.indeci.rrhh.service.PadronVacacionalService;
import com.indeci.rrhh.service.TiempoServicioService;
import com.indeci.rrhh.service.VacacionService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import com.indeci.security.util.SecurityUtil;

import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rrhh/vacaciones")
public class VacacionController {

	private final VacacionService vacacionService;
	private final TiempoServicioService tiempoServicioService;
	private final PadronVacacionalService padronVacacionalService;
	private final ImportadorVacacionesService importadorVacacionesService;

	/**
	 * SPEC_VACACIONES F8 / D7 — importa la línea base del Excel del especialista
	 * (hoja DATOS) hacia INDECI_VACACION_SALDO con ORIGEN='MIGRACION_INICIAL_2026'.
	 * Operación sensible (migración) → permiso de escritura de planilla.
	 */
	@PostMapping(value = "/importar-baseline", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
	public ApiResponse<ImportacionVacacionesResultDto> importarBaseline(
	        @RequestParam("file") MultipartFile file) throws IOException {

	    return new ApiResponse<>(
	            "OK",
	            "Importación de línea base vacacional completada",
	            importadorVacacionesService.importar(file.getBytes()));
	}

	/**
	 * SPEC_VACACIONES F4/F5 — padrón vacacional pageable (buscador server-side por DNI/nombre).
	 * @param q    texto de búsqueda (DNI o nombre); opcional.
	 * @param page índice de página (0-based).
	 * @param size tamaño de página (default 25).
	 */
	@GetMapping("/padron")
	@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
	public ApiResponse<PadronVacacionalPageDto> padron(
	        @RequestParam(required = false) String q,
	        @RequestParam(defaultValue = "0") int page,
	        @RequestParam(defaultValue = "25") int size) {

	    return new ApiResponse<>(
	            "OK",
	            "Padrón vacacional",
	            padronVacacionalService.consultar(q, page, size));
	}

	/**
	 * SPEC_VACACIONES F1 — tiempo de servicio del empleado (base para vacaciones/CTS/LBS).
	 * @param fechaCorte opcional (ISO yyyy-MM-dd); si se omite, se usa HOY.
	 */
	@GetMapping("/tiempo-servicio/{empleadoId}")
	@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
	public ApiResponse<TiempoServicioDto> tiempoServicio(
	        @PathVariable Long empleadoId,
	        @RequestParam(required = false)
	        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte) {

	    return new ApiResponse<>(
	            "OK",
	            "Tiempo de servicio calculado",
	            tiempoServicioService.calcular(empleadoId, fechaCorte));
	}

	/**
	 * Tiempo de servicio + días NO computables (LSG/faltas) + aniversario efectivo, para la
	 * sección de Configuración Remunerativa (informativo). El récord vacacional se controla
	 * en el Padrón; aquí solo se visualiza la diferencia con la antigüedad bruta.
	 */
	@GetMapping("/tiempo-servicio-detalle/{empleadoId}")
	@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
	public ApiResponse<TiempoServicioDetalleDto> tiempoServicioDetalle(
	        @PathVariable Long empleadoId) {

	    return new ApiResponse<>(
	            "OK",
	            "Tiempo de servicio con días no computables",
	            vacacionService.calcularTiempoServicioDetalle(empleadoId));
	}
	
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
	
	/**
	 * Hub Vacacional — periodos aprobados aún no gozados, disponibles para reprogramar o
	 * fraccionar (dropdown Poka-Yoke, reemplaza el ingreso manual de fechas).
	 */
	@GetMapping("/periodos-programados")
	public ApiResponse<List<PeriodoProgramadoDto>> periodosProgramados() {

	    Long empleadoId = SecurityUtil.getEmpleadoId();

	    return new ApiResponse<>(
	            "OK",
	            "Periodos programados disponibles",
	            vacacionService.listarPeriodosProgramados(empleadoId));
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

	/**
	 * Saldo proporcional para el Adelanto de Vacaciones (Art. 10 D.S. 013-2019-PCM).
	 * Alimenta el preview del modal de adelanto (Poka-Yoke) y espeja el candado del backend.
	 */
	@GetMapping("/saldo-proporcional/{empleadoId}")
	@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
	public ApiResponse<SaldoProporcionalDto> saldoProporcional(
	        @PathVariable Long empleadoId) {

	    return new ApiResponse<>(
	            "OK",
	            "Saldo proporcional para adelanto",
	            vacacionService.calcularSaldoProporcional(empleadoId));
	}

	/**
	 * Self-service ("Mis papeletas"): saldo proporcional del solicitante (JWT). Alimenta
	 * el preview del modal de Adelanto de Vacaciones sin exponer el empleadoId al cliente.
	 */
	@GetMapping("/mi-saldo-proporcional")
	public ApiResponse<SaldoProporcionalDto> miSaldoProporcional() {

	    Long empleadoId = SecurityUtil.getEmpleadoId();

	    return new ApiResponse<>(
	            "OK",
	            "Mi saldo proporcional para adelanto",
	            vacacionService.calcularSaldoProporcional(empleadoId));
	}

	@PostMapping("/padron/goce-directo")
	@PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
	public ApiResponse<Void> registrarGoceDirecto(
			@org.springframework.validation.annotation.Validated @RequestBody com.indeci.rrhh.dto.GoceDirectoDto dto) {

		vacacionService.registrarGoceDirecto(dto);

		return new ApiResponse<>(
				"OK",
				"Goce directo registrado correctamente",
				null);
	}

}
