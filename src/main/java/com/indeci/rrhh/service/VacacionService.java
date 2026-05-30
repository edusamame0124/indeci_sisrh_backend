package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SaldoVacacionalDto;
import com.indeci.rrhh.dto.VacacionDto;
import com.indeci.rrhh.dto.VacacionResponseDto;
import com.indeci.rrhh.entity.EstadoSolicitud;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.entity.Vacacion;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.repository.VacacionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VacacionService {
	
	private final VacacionRepository vacacionRepository;
	

    private final SolicitudRrhhRepository solicitudRepository;

    private final TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;

    private final EstadoSolicitudRepository estadoSolicitudRepository;
	
   /* private BigDecimal obtenerDiasGanados(
            Long empleadoId) {

        Vacacion periodo =
                obtenerPeriodoActual(
                        empleadoId);

        return calcularDiasGanados(
                periodo);
    }*/
    private BigDecimal obtenerDiasGanados(
            Long empleadoId) {

        List<Vacacion> periodos =
                vacacionRepository
                        .findByEmpleadoIdAndActivo(
                                empleadoId,
                                1);

        return periodos.stream()
                .map(this::calcularDiasGanados)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }
    
	/*private Vacacion obtenerPeriodoActual(
	        Long empleadoId) {

	    return vacacionRepository
	            .findFirstByEmpleadoIdAndActivoAndPeriodoDesdeLessThanEqualAndPeriodoHastaGreaterThanEqual(
	                    empleadoId,
	                    1,
	                    LocalDate.now(),
	                    LocalDate.now())
	            .orElseThrow(() ->
	                    new NegocioException(
	                            "No existe periodo vacacional vigente"));
	}*/
	
	private BigDecimal obtenerDiasGozados(Long empleadoId) {
		
		 TipoSolicitudRrhh vacaciones =
				 tipoSolicitudRrhhRepository
		                    .findByCodigo("VAC")
		                    .orElseThrow(() ->
		                            new NegocioException(
		                                    "Tipo VACACIONES no existe"));

		    EstadoSolicitud aprobado =
		            estadoSolicitudRepository
		                    .findByCodigo("APROBADO_RRHH")
		                    .orElseThrow(() ->
		                            new NegocioException(
		                                    "Estado APROBADO_RRHH no existe"));

		    List<SolicitudRrhh> solicitudes =
		            solicitudRepository
		                    .findByEmpleadoIdAndTipoSolicitudIdAndEstadoSolicitudIdAndActivo(
		                            empleadoId,
		                            vacaciones.getId(),
		                            aprobado.getId(),
		                            1);

		    double total =
		            solicitudes.stream()
		                    .mapToDouble(s ->
		                            s.getCantidadDias() == null
		                                    ? 0
		                                    : s.getCantidadDias())
		                    .sum();

		    return BigDecimal.valueOf(total);
	}
	
	public SaldoVacacionalDto obtenerSaldoVacacional(
	        Long empleadoId) {

	    BigDecimal ganados =
	            obtenerDiasGanados(
	                    empleadoId);

	    BigDecimal gozados =
	            obtenerDiasGozados(
	                    empleadoId);
	    
	    BigDecimal saldoVacacional=ganados.subtract(gozados);
	    
	    SaldoVacacionalDto dto=new SaldoVacacionalDto();
	    
	    dto.setDiasGanados(ganados);
	    dto.setDiasGozados(gozados);
	    dto.setSaldo(saldoVacacional);
	    

	    return  dto;
	}
	
	
	public List<VacacionResponseDto>
	listarPorEmpleado(Long empleadoId) {

	    return vacacionRepository
	            .findByEmpleadoIdAndActivo(
	                    empleadoId,
	                    1)
	            .stream()
	            .map(this::convertir)
	            .toList();
	}
	
	private VacacionResponseDto
	convertir(Vacacion v) {

	    VacacionResponseDto dto =
	            new VacacionResponseDto();

	    dto.setId(v.getId());

	    dto.setEmpleadoId(
	            v.getEmpleadoId());

	    if(v.getEmpleado() != null) {

	        dto.setEmpleado(
	                v.getEmpleado()
	                 .getCodigoInterno());
	    }

	    dto.setPeriodo(
	            v.getPeriodo());

	    dto.setPeriodoDesde(
	            v.getPeriodoDesde());

	    dto.setPeriodoHasta(
	            v.getPeriodoHasta());

	    dto.setDiasGanados(
	            calcularDiasGanados(v));

	    dto.setObservacion(
	            v.getObservacion());

	    dto.setActivo(
	            v.getActivo());

	    return dto;
	}
	
	public void registrar(VacacionDto dto) {
		
		if(vacacionRepository.existsByEmpleadoIdAndPeriodo(
				dto.getEmpleadoId(),
				dto.getPeriodo())) {
			
			 throw new NegocioException(
			            "Ya existe el periodo vacacional");
		}
		
		Vacacion objVacacion=new Vacacion();
		
	
	
		objVacacion.setEmpleadoId(dto.getEmpleadoId());
		objVacacion.setObservacion(dto.getObservacion());
		objVacacion.setPeriodo(dto.getPeriodo());
		objVacacion.setActivo(1);
		objVacacion.setPeriodoDesde(dto.getPeriodoDesde());
		objVacacion.setPeriodoHasta(dto.getPeriodoHasta());
		
		
		if(dto.getPeriodoHasta()
		        .isBefore(
		                dto.getPeriodoDesde())) {

		    throw new NegocioException(
		            "La fecha fin no puede ser menor que la fecha inicio");
		}
		vacacionRepository.save(
		        objVacacion);
	}
	
	public BigDecimal calcularDiasGanados(
	        Vacacion periodo) {

	    LocalDate hoy = LocalDate.now();

	    if(hoy.isBefore(
	            periodo.getPeriodoDesde())) {

	        return BigDecimal.ZERO;
	    }

	    LocalDate finCalculo =
	            hoy.isAfter(periodo.getPeriodoHasta())
	                    ? periodo.getPeriodoHasta()
	                    : hoy;

	    long meses =
	            ChronoUnit.MONTHS.between(
	                    periodo.getPeriodoDesde(),
	                    finCalculo);
	    
	    if(meses < 0) {
	        meses = 0;
	    }

	    return BigDecimal
	            .valueOf(meses)
	            .multiply(
	                    BigDecimal.valueOf(2.5));
	}

}
