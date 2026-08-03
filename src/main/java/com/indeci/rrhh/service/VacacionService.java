package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.exception.VinculoNoEncontradoException;
import com.indeci.rrhh.dto.AcumulacionDecisionInputDto;
import com.indeci.rrhh.dto.AcumulacionDecisionResponseDto;
import com.indeci.rrhh.dto.CorreccionGozadosResultDto;
import com.indeci.rrhh.dto.CorregirGozadosDto;
import com.indeci.rrhh.dto.DiasNoComputablesDto;
import com.indeci.rrhh.dto.PeriodoProgramadoDto;
import com.indeci.rrhh.dto.SaldoProporcionalDto;
import com.indeci.rrhh.dto.SaldoVacacionalDto;
import com.indeci.rrhh.dto.TiempoServicioDetalleDto;
import com.indeci.rrhh.dto.TiempoServicioDto;
import com.indeci.rrhh.dto.VacacionDto;
import com.indeci.rrhh.dto.VacacionResponseDto;
import com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta;
import com.indeci.rrhh.service.support.Dias360;
import com.indeci.rrhh.entity.EstadoSolicitud;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.entity.Vacacion;
import com.indeci.rrhh.entity.VacacionAcumulacionDecision;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.repository.VacacionAcumulacionDecisionRepository;
import com.indeci.rrhh.repository.VacacionRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;
import com.indeci.rrhh.repository.SolicitudVacacionDetRepository;
import com.indeci.rrhh.entity.SolicitudRrhhDoc;
import com.indeci.rrhh.entity.SolicitudVacacionDet;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.security.util.SecurityUtil;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VacacionService {
	
	private final VacacionRepository vacacionRepository;
	

    private final SolicitudRrhhRepository solicitudRepository;

    private final TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;

    private final EstadoSolicitudRepository estadoSolicitudRepository;

    private final VacacionSaldoRepository vacacionSaldoRepository;

    private final SolicitudVacacionDetRepository solicitudVacacionDetRepository;

    private final TiempoServicioService tiempoServicioService;

    private final IncidenciaLaboralCompuesta incidenciaLaboralCompuesta;

    private final VacacionAcumulacionDecisionRepository acumulacionDecisionRepository;

    private final AuditoriaContext auditoriaContext;

    /** F9.3 — D.S. 013-2019-PCM: tope de períodos vacacionales acumulables sin gozar. */
    public static final int TOPE_PERIODOS_ACUMULACION = 2;

    /** Devengo mensual (D.S. 013-2019-PCM Art. 10): 30 días / 12 meses = 2.5 días por mes completo. */
    private static final BigDecimal DIAS_POR_MES = BigDecimal.valueOf(2.5);

    /** Hub Vacacional — marca el período origen como ya reprogramado/fraccionado (no disponible). */
    private static final String ESTADO_SUSTITUIDO = "SUSTITUIDO";

    /** Botón "Editar Gozados" del Padrón — corrección manual del total de días gozados. */
    private static final String ORIGEN_CORRECCION_MANUAL = "CORRECCION_MANUAL_RRHH";

    /** Marca el registro histórico como un ajuste administrativo, no un goce físico real. */
    private static final String TIPO_GOCE_CORRECCION = "CORRECCION_MANUAL";
	
	/**
	 * Obtenidos/Gozados/Saldo — SIEMPRE visible en toda papeleta (pedido RR.HH.).
	 *
	 * <p><b>Fuente:</b> {@code INDECI_VACACION_SALDO} (vía {@link VacacionSaldoRepository}) —
	 * la MISMA tabla que llena {@code VacacionProvisionService.provisionar()} (récord neto de
	 * LSG/faltas) y que ya usa {@link #saldoDisponible} para validar aprobaciones. Antes este
	 * método calculaba un devengo aparte dependiendo de {@code TipoSolicitudRrhh.codigo="VAC"}
	 * — código que NO existe en el catálogo real (el código vigente es {@code "012"|padded
	 * "012"}) — por lo que siempre lanzaba {@link NegocioException} y el saldo no se mostraba.</p>
	 */
	public SaldoVacacionalDto obtenerSaldoVacacional(
	        Long empleadoId) {

	    List<com.indeci.rrhh.entity.VacacionSaldo> saldos =
	            vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(empleadoId, 1);

	    BigDecimal ganados = saldos.stream()
	            .map(s -> s.getDiasGanados() != null ? BigDecimal.valueOf(s.getDiasGanados()) : BigDecimal.ZERO)
	            .reduce(BigDecimal.ZERO, BigDecimal::add);

	    BigDecimal gozados = saldos.stream()
	            .map(s -> s.getDiasGozados() != null ? BigDecimal.valueOf(s.getDiasGozados()) : BigDecimal.ZERO)
	            .reduce(BigDecimal.ZERO, BigDecimal::add);

	    SaldoVacacionalDto dto = new SaldoVacacionalDto();
	    dto.setDiasGanados(ganados);
	    dto.setDiasGozados(gozados);
	    dto.setSaldo(ganados.subtract(gozados));
	    return dto;
	}
	
	
	/**
	 * Saldo PROPORCIONAL disponible para un Adelanto de Vacaciones — Art. 10 del
	 * D.S. 013-2019-PCM. Transversal a 276 / CAS / SERVIR: {@code mesesEfectivos × 2.5}.
	 *
	 * <p>El "período de devengo en curso" es el año incompleto actual (se descuentan los
	 * años ya cumplidos, que generan su bloque de 30 días por la provisión). Los meses se
	 * derivan del tiempo de servicio 30/360 (F1) y se <b>netean de incidencias no
	 * computables</b> (LSG + faltas injustificadas, mismo {@link IncidenciaLaboralProvider}
	 * del récord). Finalmente se restan los adelantos ya tomados en el período para no
	 * permitir sobregiro.</p>
	 *
	 * @return proporcional devengado y tope disponible; ceros si el empleado no tiene vínculo.
	 */
	@Transactional(readOnly = true)
	public SaldoProporcionalDto calcularSaldoProporcional(Long empleadoId) {
	    final TiempoServicioDto ts;
	    try {
	        ts = tiempoServicioService.calcular(empleadoId, null); // null ⇒ HOY
	    } catch (VinculoNoEncontradoException e) {
	        return new SaldoProporcionalDto(0, BigDecimal.ZERO, 0d, 0d);
	    }

	    // Período de devengo EN CURSO = año incompleto actual (meses + días 30/360).
	    final int diasSpanPeriodo = ts.meses() * 30 + ts.dias();
	    final LocalDate periodoInicio = ts.fechaIngreso().plusYears(ts.anios());

	    // Netear incidencias no computables (LSG + faltas) del período en curso.
	    final int diasNoComputables = incidenciaLaboralCompuesta
	            .obtenerDiasNoComputables(empleadoId, periodoInicio, ts.fechaCorte());
	    final int diasEfectivos = Math.max(0, diasSpanPeriodo - diasNoComputables);

	    // Devengo por MES COMPLETO trabajado (2.5 días/mes). Tope 12 meses.
	    final int mesesEfectivos = Math.min(12, diasEfectivos / 30);
	    final BigDecimal proporcional = DIAS_POR_MES.multiply(BigDecimal.valueOf(mesesEfectivos));

	    // Adelantos ya gozados dentro del período en curso (no reincidir).
	    final double adelantados = vacacionRepository.findByEmpleadoIdAndActivo(empleadoId, 1).stream()
	            .filter(v -> v.getEsAdelanto() != null && v.getEsAdelanto() == 1)
	            .filter(v -> v.getPeriodoDesde() != null && !v.getPeriodoDesde().isBefore(periodoInicio))
	            .mapToDouble(v -> v.getDias() != null ? v.getDias() : 0d)
	            .sum();

	    final double disponible = Math.max(0d, proporcional.doubleValue() - adelantados);
	    return new SaldoProporcionalDto(mesesEfectivos, proporcional, adelantados, disponible);
	}

	/**
	 * Tiempo de servicio bruto + días NO computables (LSG/faltas/suspensiones) + tiempo de
	 * servicio EFECTIVO + aniversario efectivo estimado, para Configuración Remunerativa
	 * (V012_42 F1). El aniversario efectivo se estima corriendo el próximo aniversario nominal
	 * por el total de días no computables. El tiempo EFECTIVO se calcula aparte, en este DTO:
	 * bruto (30/360, sin tocar {@link TiempoServicioDto}) − no computables, re-desglosado con
	 * la misma fórmula 30/360 ({@link Dias360#desglosar}) — ver javadoc de
	 * {@link TiempoServicioDetalleDto} sobre por qué NO se modifica el record bruto.
	 */
	@Transactional(readOnly = true)
	public TiempoServicioDetalleDto calcularTiempoServicioDetalle(Long empleadoId) {
	    final TiempoServicioDto ts;
	    try {
	        ts = tiempoServicioService.calcular(empleadoId, null);
	    } catch (VinculoNoEncontradoException e) {
	        return TiempoServicioDetalleDto.sinVinculo();
	    }
	    final DiasNoComputablesDto noComp = incidenciaLaboralCompuesta
	            .calcularDesglose(empleadoId, ts.fechaIngreso(), ts.fechaCorte());
	    final LocalDate aniversarioEfectivo = ts.fechaIngreso()
	            .plusYears(ts.anios() + 1L)
	            .plusDays(noComp.total());
	    final int totalDiasEfectivos = Math.max(0, ts.totalDias360() - noComp.total());
	    final Dias360.AniosMesesDias efectivo = Dias360.desglosar(totalDiasEfectivos);
	    return new TiempoServicioDetalleDto(
	            ts, noComp, aniversarioEfectivo,
	            efectivo.anios(), efectivo.meses(), efectivo.dias(), totalDiasEfectivos);
	}

	/**
	 * Hub Vacacional — periodos aprobados aún no gozados (fecha de inicio futura) y no
	 * sustituidos por una reprogramación/fraccionamiento previa. Fuente del dropdown
	 * Poka-Yoke que reemplaza el ingreso manual de fechas en REPROG_ACTUAL/FRACC_ACTUAL.
	 */
	@Transactional(readOnly = true)
	public List<PeriodoProgramadoDto> listarPeriodosProgramados(Long empleadoId) {
	    return vacacionRepository
	            .findByEmpleadoIdAndActivoAndPeriodoDesdeGreaterThanEqualAndEstadoNot(
	                    empleadoId, 1, LocalDate.now(), ESTADO_SUSTITUIDO)
	            .stream()
	            .map(v -> new PeriodoProgramadoDto(
	                    v.getId(), v.getPeriodoDesde(), v.getPeriodoHasta(),
	                    v.getDias(), v.getTipoGoce()))
	            .toList();
	}

	// ══════════════════════════════════════════════════════════════════════════
	// F9.3 — Acumulación de vacaciones (D.S. 013-2019-PCM): hasta 2 períodos sin
	// gozar; el exceso NUNCA se pierde/bloquea automáticamente — se marca para
	// evaluación de RR.HH. con sustento (auditoría append-only, no toca saldos).
	// ══════════════════════════════════════════════════════════════════════════

	/**
	 * Cuenta cuántos períodos (años) tienen saldo pendiente de gozar
	 * ({@code diasGanados > diasGozados}). El consumo de saldo es FIFO por año
	 * ({@link #descontarSaldoVacacional}), así que esto refleja fielmente cuántos
	 * "períodos completos sin tocar" tiene el empleado — no una simple resta global.
	 */
	public static int contarPeriodosPendientes(List<VacacionSaldo> saldos) {
	    return (int) saldos.stream()
	            .filter(s -> (s.getDiasGanados() != null ? s.getDiasGanados() : 0d)
	                    > (s.getDiasGozados() != null ? s.getDiasGozados() : 0d))
	            .count();
	}

	@Transactional(readOnly = true)
	public int calcularPeriodosAcumulados(Long empleadoId) {
	    return contarPeriodosPendientes(
	            vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(empleadoId, 1));
	}

	/**
	 * Registra la decisión de RR.HH. sobre la acumulación de un empleado (auditoría
	 * append-only). NO modifica ningún {@link VacacionSaldo} — es exclusivamente un
	 * registro de trazabilidad; cualquier ajuste real de saldo sigue pasando por los
	 * flujos existentes (papeleta de vacaciones / Goce Directo).
	 */
	@Transactional
	public AcumulacionDecisionResponseDto registrarDecisionAcumulacion(
	        Long empleadoId, AcumulacionDecisionInputDto dto) {
	    int periodosPendientes = calcularPeriodosAcumulados(empleadoId);

	    VacacionAcumulacionDecision entity = new VacacionAcumulacionDecision();
	    entity.setEmpleadoId(empleadoId);
	    entity.setPeriodosPendientesAlMomento(periodosPendientes);
	    entity.setMotivoDecision(dto.getMotivoDecision());
	    entity.setDocumentoSustento(dto.getDocumentoSustento());
	    entity.setUsuarioRegistro(SecurityUtil.getUsername());
	    entity.setActivo(1);
	    acumulacionDecisionRepository.save(entity);

	    // CREATED_AT es default de BD (insertable=false) — se aproxima con "ahora" para la
	    // respuesta inmediata; el historial (listarDecisionesAcumulacion) sí trae el valor real.
	    return new AcumulacionDecisionResponseDto(
	            entity.getId(), empleadoId, periodosPendientes,
	            entity.getMotivoDecision(), entity.getDocumentoSustento(),
	            entity.getUsuarioRegistro(), java.time.LocalDateTime.now());
	}

	@Transactional(readOnly = true)
	public List<AcumulacionDecisionResponseDto> listarDecisionesAcumulacion(Long empleadoId) {
	    return acumulacionDecisionRepository
	            .findByEmpleadoIdAndActivoOrderByCreatedAtDesc(empleadoId, 1)
	            .stream()
	            .map(d -> new AcumulacionDecisionResponseDto(
	                    d.getId(), d.getEmpleadoId(), d.getPeriodosPendientesAlMomento(),
	                    d.getMotivoDecision(), d.getDocumentoSustento(), d.getUsuarioRegistro(),
	                    d.getCreatedAt()))
	            .toList();
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

    /**
     * Valida el saldo vacacional ANTES de aprobar una papeleta (D.Leg. 1405).
     *
     * <p>Fuente: {@code INDECI_VACACION_SALDO} — la MISMA tabla que descuenta el motor
     * ({@link #descontarSaldoVacacional}) y que refleja el Padrón, para que validación y
     * descuento sean consistentes. (No se usa {@code obtenerSaldoVacacional}, que calcula
     * un devengo aparte y depende del código "VAC".)
     *
     * <p>Los ADELANTOS no validan saldo: por definición se gozan sin saldo previo.
     *
     * <p><b>Neutralización de reprogramación/fraccionamiento:</b> el detalle "_ACTUAL"
     * (REPROG_ACTUAL/FRACC_ACTUAL) representa el periodo previo ya descontado por su
     * papeleta original; al reprogramarse se libera. El consumo real es NETO:
     * <pre>neto = díasNuevos − díasOriginales(_ACTUAL) ; SaldoEfectivo = disponible − neto ≥ 0</pre>
     * Programación/adelanto no tienen "_ACTUAL" ⇒ neto = díasNuevos. Reprogramación/
     * fraccionamiento (regla de negocio díasNuevos == díasOriginales) ⇒ neto = 0 ⇒ jamás
     * un falso "saldo insuficiente".
     */
    public void validarSaldoAprobacion(SolicitudRrhh solicitud) {
        List<SolicitudVacacionDet> detalles =
                solicitudVacacionDetRepository.findBySolicitudIdAndActivo(solicitud.getId(), 1);

        boolean esAdelanto = detalles.stream()
                .anyMatch(d -> "ADELANTO".equalsIgnoreCase(d.getTipo()));
        if (esAdelanto) {
            // Art. 10 D.S. 013-2019-PCM: el adelanto no requiere saldo GANADO, pero NO puede
            // exceder el saldo PROPORCIONAL acumulado (mes efectivo × 2.5) — prohíbe el sobregiro.
            double diasAdelanto = sumaDetalles(detalles, false);
            if (diasAdelanto <= 0) {
                return;
            }
            SaldoProporcionalDto prop = calcularSaldoProporcional(solicitud.getEmpleadoId());
            if (diasAdelanto > prop.saldoDisponible()) {
                throw new NegocioException(
                        "El adelanto de vacaciones excede el saldo proporcional acumulado "
                                + "(Art. 10 D.S. 013-2019-PCM): solicita " + fmtDias(diasAdelanto)
                                + " día(s) y dispone de " + fmtDias(prop.saldoDisponible())
                                + " (" + prop.mesesEfectivos() + " mes(es) efectivo(s) × 2.5"
                                + (prop.diasAdelantados() > 0
                                        ? "; ya adelantó " + fmtDias(prop.diasAdelantados())
                                        : "")
                                + ").");
            }
            return;
        }

        double netoRequerido = consumoNeto(detalles);
        if (netoRequerido <= 0) {
            return;
        }

        double disponible = saldoDisponible(solicitud.getEmpleadoId());
        if (netoRequerido > disponible) {
            throw new NegocioException(
                    "Saldo vacacional insuficiente: requiere " + fmtDias(netoRequerido)
                            + " día(s) neto(s) y dispone de " + fmtDias(disponible)
                            + ". Si corresponde un adelanto, regístrelo como tal.");
        }
    }

    /** Consumo NETO de saldo de una papeleta: díasNuevos(goce real) − díasOriginales(_ACTUAL liberados). */
    private double consumoNeto(List<SolicitudVacacionDet> detalles) {
        return sumaDetalles(detalles, false) - sumaDetalles(detalles, true);
    }

    /** Suma días: {@code soloActual=true} → solo "_ACTUAL"; {@code false} → solo periodos de goce real. */
    private double sumaDetalles(List<SolicitudVacacionDet> detalles, boolean soloActual) {
        return detalles.stream()
                .filter(d -> esDetalleActual(d) == soloActual)
                .mapToDouble(this::diasParaSaldo)
                .sum();
    }

    /**
     * Días que efectivamente consumen/liberan saldo anual: CALENDARIO (Art. 34), no el conteo
     * HÁBIL del pool de fraccionamiento (Art. 35.b/c). Para Programación/Adelanto/Reprogramación
     * ambos valores coinciden (totalDias ya es calendario); para Fraccionamiento difieren y aquí
     * se usa el calendario. Fallback a totalDias si diasCalendario no se calculó (defensivo).
     */
    private double diasParaSaldo(SolicitudVacacionDet d) {
        if (d.getDiasCalendario() != null) {
            return d.getDiasCalendario();
        }
        return d.getTotalDias() != null ? d.getTotalDias() : 0d;
    }

    /** ¿El detalle es el periodo previo que se reprograma/fracciona (REPROG_ACTUAL/FRACC_ACTUAL)? */
    private boolean esDetalleActual(SolicitudVacacionDet det) {
        return det.getTipo() != null && det.getTipo().endsWith("_ACTUAL");
    }

    /** Saldo disponible del empleado desde INDECI_VACACION_SALDO (Σ ganados − Σ gozados). */
    private double saldoDisponible(Long empleadoId) {
        return vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(empleadoId, 1).stream()
                .mapToDouble(s -> (s.getDiasGanados() != null ? s.getDiasGanados() : 0d)
                        - (s.getDiasGozados() != null ? s.getDiasGozados() : 0d))
                .sum();
    }

    private String fmtDias(double dias) {
        return dias == Math.floor(dias)
                ? String.valueOf((long) dias)
                : String.valueOf(dias);
    }

    @Transactional
    public void procesarAprobacionVacaciones(SolicitudRrhh solicitud, SolicitudRrhhDoc docSustento) {
        List<SolicitudVacacionDet> detalles = solicitudVacacionDetRepository.findBySolicitudIdAndActivo(solicitud.getId(), 1);

        // Histórico de goce: SOLO los periodos de goce reales. El "_ACTUAL"
        // (REPROG_ACTUAL/FRACC_ACTUAL) es el periodo previo que se reprograma —ya
        // descontado por su papeleta original—, por lo que no genera un nuevo registro
        // de goce ni se descuenta de nuevo (evita la "trampa del doble descuento").
        for (SolicitudVacacionDet det : detalles) {
            if (esDetalleActual(det)) {
                // Hub Vacacional — marca el período origen (elegido del dropdown Poka-Yoke)
                // como SUSTITUIDO para que deje de aparecer disponible; evita que el mismo
                // período se reprograme/fraccione dos veces (duplicaría el beneficio).
                if (det.getVacacionOrigenId() != null) {
                    Vacacion origen = vacacionRepository.findById(det.getVacacionOrigenId())
                            .orElseThrow(() -> new NegocioException(
                                    "Período origen no encontrado: " + det.getVacacionOrigenId()));
                    // Defensa en profundidad (IDOR): el id que manda el cliente nunca se
                    // confía ciegamente — debe pertenecer al mismo empleado de la solicitud.
                    if (!java.util.Objects.equals(origen.getEmpleadoId(), solicitud.getEmpleadoId())) {
                        throw new NegocioException(
                                "El período origen no pertenece al solicitante");
                    }
                    origen.setEstado(ESTADO_SUSTITUIDO);
                    vacacionRepository.save(origen);
                }
                continue;
            }
            Vacacion vacacion = new Vacacion();
            vacacion.setEmpleadoId(solicitud.getEmpleadoId());
            vacacion.setPeriodoDesde(det.getFechaInicio());
            vacacion.setPeriodoHasta(det.getFechaFin());
            vacacion.setPeriodo(String.valueOf(det.getFechaInicio().getYear()));
            vacacion.setObservacion(solicitud.getObservacion());
            vacacion.setActivo(1);

            vacacion.setAnioPeriodo(det.getFechaInicio().getYear());
            vacacion.setTipoGoce(det.getTipo());
            vacacion.setDias(det.getTotalDias() != null ? det.getTotalDias() : 0d);
            vacacion.setDiasCalendario(det.getDiasCalendario());
            vacacion.setEstado("GOZADO");
            vacacion.setEsAdelanto("ADELANTO".equals(det.getTipo()) ? 1 : 0);
            vacacion.setOrigen("MOTOR");
            vacacion.setSolicitudRrhhId(solicitud.getId());

            if (docSustento != null) {
                vacacion.setDocumentoSustento(docSustento.getNombreArchivo());
            }

            vacacionRepository.save(vacacion);
        }

        // Consumo NETO de saldo (misma fórmula que la validación): díasNuevos − díasOriginales(_ACTUAL).
        // Programación/adelanto ⇒ neto = díasNuevos (idéntico al comportamiento previo).
        // Reprogramación/fraccionamiento ⇒ neto = 0 (no toca el saldo).
        ajustarSaldoVacacional(solicitud.getEmpleadoId(), consumoNeto(detalles));
    }

    /**
     * Botón "Editar Gozados" del Padrón Vacacional — RR.HH. corrige el TOTAL de días gozados
     * de un empleado a un valor arbitrario (dato migrado incompleto, error de digitación, goce
     * gestionado fuera del flujo de papeletas, etc.). El motivo es obligatorio (Poka-Yoke).
     *
     * <p>Reutiliza {@link #ajustarSaldoVacacional} — el MISMO reparto FIFO que ya usa la
     * aprobación de papeletas y "Goce Directo" — por lo que Saldo/Récord (derivados en vivo por
     * {@code VacacionCalculoService}) quedan recalculados sin tocar código adicional.</p>
     *
     * <p>Trazabilidad: además del registro en AUDITORIA (vía {@code @Auditable}), se inserta una
     * fila en {@code INDECI_VACACIONES} ({@code ORIGEN=CORRECCION_MANUAL_RRHH}) con el DELTA
     * aplicado en {@code diasCalendario} (el campo que efectivamente mueve el saldo). Se deja
     * {@code dias} (hábiles) en {@code null} a propósito: ese campo alimenta el pool de 7 días
     * fraccionables (Art. 35) y una corrección administrativa no es un goce fraccionado real —
     * tocarlo distorsionaría esa validación normativa (CONSTRAINT: no modificar lógica existente).</p>
     */
    @Auditable(accion = "CORREGIR_GOZADOS_MANUAL")
    @Transactional
    public CorreccionGozadosResultDto corregirGozadoManual(Long empleadoId, CorregirGozadosDto dto) {
        if (dto.getMotivo() == null || dto.getMotivo().isBlank()) {
            throw new NegocioException("El motivo de la corrección es obligatorio");
        }
        if (dto.getNuevoTotalGozado() == null || dto.getNuevoTotalGozado() < 0d) {
            throw new NegocioException("El nuevo total de días gozados no puede ser negativo");
        }

        List<VacacionSaldo> saldos = vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(empleadoId, 1);
        if (saldos.isEmpty()) {
            throw new NegocioException("El empleado no tiene saldo vacacional provisionado");
        }

        double totalActual = saldos.stream()
                .mapToDouble(s -> s.getDiasGozados() != null ? s.getDiasGozados() : 0d)
                .sum();
        double nuevoTotal = dto.getNuevoTotalGozado();
        double delta = nuevoTotal - totalActual;

        if (delta != 0d) {
            ajustarSaldoVacacional(empleadoId, delta);

            LocalDate hoy = LocalDate.now();
            Vacacion correccion = new Vacacion();
            correccion.setEmpleadoId(empleadoId);
            correccion.setPeriodoDesde(hoy);
            correccion.setPeriodoHasta(hoy);
            correccion.setPeriodo(String.valueOf(hoy.getYear()));
            correccion.setAnioPeriodo(hoy.getYear());
            correccion.setTipoGoce(TIPO_GOCE_CORRECCION);
            correccion.setDiasCalendario(delta);
            correccion.setEstado("GOZADO");
            correccion.setEsAdelanto(0);
            correccion.setOrigen(ORIGEN_CORRECCION_MANUAL);
            correccion.setMotivoExcepcion(dto.getMotivo());
            correccion.setObservacion(String.format(
                    "Corrección manual RR.HH.: %.1f -> %.1f días gozados. Motivo: %s",
                    totalActual, nuevoTotal, dto.getMotivo()));
            correccion.setActivo(1);
            vacacionRepository.save(correccion);
        }

        auditoriaContext.setDetalle(String.format(
                "Corrección manual de días gozados — empleado %d. Anterior: %.1f. Nuevo: %.1f. Delta: %.1f. Motivo: %s",
                empleadoId, totalActual, nuevoTotal, delta, dto.getMotivo()));

        return new CorreccionGozadosResultDto(totalActual, nuevoTotal, delta);
    }

    /** Ajusta el saldo: {@code delta>0} consume (descuenta), {@code delta<0} libera (acredita), {@code 0} no-op. */
    private void ajustarSaldoVacacional(Long empleadoId, double delta) {
        if (delta > 0) {
            descontarSaldoVacacional(empleadoId, delta);
        } else if (delta < 0) {
            acreditarSaldoVacacional(empleadoId, -delta);
        }
    }

    /** Libera saldo (reduce diasGozados) — reversa del consumo, desde el periodo más reciente. */
    private void acreditarSaldoVacacional(Long empleadoId, double diasLiberar) {
        List<VacacionSaldo> saldos = vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(empleadoId, 1);
        double restante = diasLiberar;
        for (int i = saldos.size() - 1; i >= 0 && restante > 0; i--) {
            VacacionSaldo saldo = saldos.get(i);
            double gozados = saldo.getDiasGozados() != null ? saldo.getDiasGozados() : 0.0;
            if (gozados <= 0) {
                continue;
            }
            double aLiberar = Math.min(gozados, restante);
            saldo.setDiasGozados(gozados - aLiberar);
            vacacionSaldoRepository.save(saldo);
            restante -= aLiberar;
        }
    }

    private void descontarSaldoVacacional(Long empleadoId, double diasDescontar) {
        List<VacacionSaldo> saldos = vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(empleadoId, 1);
        double diasRestantes = diasDescontar;

        for (VacacionSaldo saldo : saldos) {
            if (diasRestantes <= 0) break;

            double diasGanados = saldo.getDiasGanados() != null ? saldo.getDiasGanados() : 0.0;
            double diasGozados = saldo.getDiasGozados() != null ? saldo.getDiasGozados() : 0.0;
            double saldoDisponible = diasGanados - diasGozados;

            if (saldoDisponible > 0) {
                double aDescontar = Math.min(saldoDisponible, diasRestantes);
                saldo.setDiasGozados(diasGozados + aDescontar);
                vacacionSaldoRepository.save(saldo);
                diasRestantes -= aDescontar;
            }
        }

        // Si aún quedan días y no hay más saldo, se podría registrar en un saldo adelantado,
        // pero por ahora lo registramos en el saldo más reciente (el último de la lista)
        if (diasRestantes > 0 && !saldos.isEmpty()) {
            VacacionSaldo ultimoSaldo = saldos.get(saldos.size() - 1);
            double gozadosAcumulados = ultimoSaldo.getDiasGozados() != null ? ultimoSaldo.getDiasGozados() : 0.0;
            ultimoSaldo.setDiasGozados(gozadosAcumulados + diasRestantes);
            vacacionSaldoRepository.save(ultimoSaldo);
        }
    }

    @Transactional
    public void registrarGoceDirecto(com.indeci.rrhh.dto.GoceDirectoDto dto) {
        if (dto.getFechaFin().isBefore(dto.getFechaInicio())) {
            throw new NegocioException("La fecha fin no puede ser menor que la fecha inicio");
        }

        int diasTotales = (int) ChronoUnit.DAYS.between(dto.getFechaInicio(), dto.getFechaFin()) + 1;

        if (!Boolean.TRUE.equals(dto.getEsAdelanto())) {
            SaldoVacacionalDto saldo = obtenerSaldoVacacional(dto.getEmpleadoId());
            if (BigDecimal.valueOf(diasTotales).compareTo(saldo.getSaldo()) > 0) {
                throw new NegocioException("El empleado no cuenta con saldo vacacional suficiente para " + diasTotales + " días. Marque como adelanto si aplica.");
            }
        }

        // F9 - Fraccionamiento Normado D.S. 013-2019-PCM
        if (diasTotales < 7) {
            if (dto.getMotivoExcepcion() == null || dto.getMotivoExcepcion().trim().isEmpty()) {
                int anioPeriodo = dto.getFechaInicio().getYear();
                List<Vacacion> vacacionesAnio = vacacionRepository.findByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1)
                        .stream().filter(v -> v.getAnioPeriodo() != null && v.getAnioPeriodo() == anioPeriodo).toList();
                
                double diasFraccionadosUsados = vacacionesAnio.stream()
                        .filter(v -> v.getDias() != null && v.getDias() < 7)
                        .mapToDouble(Vacacion::getDias)
                        .sum();

                if (diasFraccionadosUsados + diasTotales > 7) {
                    throw new com.indeci.rrhh.exception.FraccionamientoIlegalException(
                            "El servidor civil ya agotó su pool de 7 días fraccionables para este periodo. " +
                            "Bloques adicionales deben ser de mínimo 7 días consecutivos. " +
                            "Provea una justificación de excepción (bypass) para forzar el registro.");
                }
            }
        }

        Vacacion vacacion = new Vacacion();
        vacacion.setEmpleadoId(dto.getEmpleadoId());
        vacacion.setPeriodoDesde(dto.getFechaInicio());
        vacacion.setPeriodoHasta(dto.getFechaFin());
        vacacion.setPeriodo(String.valueOf(dto.getFechaInicio().getYear()));
        vacacion.setObservacion(dto.getMotivoExcepcion());
        vacacion.setActivo(1);
        
        vacacion.setAnioPeriodo(dto.getFechaInicio().getYear());
        vacacion.setTipoGoce(Boolean.TRUE.equals(dto.getEsAdelanto()) ? "ADELANTO" : "REGULAR");
        vacacion.setDias((double) diasTotales);
        vacacion.setEstado("GOZADO");
        vacacion.setEsAdelanto(Boolean.TRUE.equals(dto.getEsAdelanto()) ? 1 : 0);
        vacacion.setOrigen("OVERRIDE_RRHH");
        vacacion.setDocumentoSustento(dto.getDocumentoSustento());
        vacacion.setMotivoExcepcion(dto.getMotivoExcepcion());

        vacacionRepository.save(vacacion);

        descontarSaldoVacacional(dto.getEmpleadoId(), diasTotales);
    }

}
