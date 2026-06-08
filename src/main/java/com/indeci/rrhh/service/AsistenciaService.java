package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.dto.AsistenciaGuardarDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaResumenCalculator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Módulo M04 — Asistencia y Control de Tiempo (SPEC §12.2 PANTALLA-02).
 *
 * Captura el calendario mensual de cada empleado y calcula el descuento por
 * tardanzas y faltas según D.Leg. 276 Art. 24 (REGLA 276-02):
 *   descuento_tardanza = ROUND((remuneracion/30/8/60) * total_minutos, 2)
 *   descuento_falta    = ROUND((remuneracion/30) * dias_falta, 2)
 *
 * El motor M05 (PASO 7) consumirá estos valores cuando se integre.
 */
@Service
@RequiredArgsConstructor
public class AsistenciaService {

    private final AsistenciaCabeceraRepository cabeceraRepository;
    private final AsistenciaDetalleRepository detalleRepository;
    private final AuditoriaContext auditoriaContext;

    /** Tipos de día válidos (espejo del CHECK INDECI_ASIST_DET_TIPO_CK). */
    private static final Set<String> TIPOS_DIA = Set.of(
            "LABORAL", "FALTA", "TARDANZA", "LICENCIA", "VACACIONES", "DESCANSO",
            "FERIADO", "OBSERVADO");

    private static final Set<String> ESTADOS_CABECERA = Set.of(
            "BORRADOR", "PREVALIDADA", "LISTA_PARA_VALIDAR", "OBSERVADA", "VALIDADA");

    /** Tipos que cuentan como día efectivamente laborado. */
    private static final Set<String> TIPOS_LABORADOS = Set.of("LABORAL", "TARDANZA");

    // ==========================================
    // CONSULTAR (empleado + período)
    // ==========================================

    /**
     * Devuelve la asistencia del empleado/período. Si aún no existe, retorna un
     * DTO con id null y lista de días vacía (calendario nuevo).
     */
    public AsistenciaResponseDto obtener(Long empleadoId, String periodo) {

        return cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .map(this::aResponse)
                .orElseGet(() -> {
                    AsistenciaResponseDto vacio = new AsistenciaResponseDto();
                    vacio.setEmpleadoId(empleadoId);
                    vacio.setPeriodo(periodo);
                    vacio.setEstado("BORRADOR");
                    vacio.setDiasLaborados(0);
                    vacio.setDiasFalta(0);
                    vacio.setTotalMinTardanza(0);
                    vacio.setDescuentoTardanza(0.0);
                    vacio.setDescuentoFalta(0.0);
                    vacio.setDias(new ArrayList<>());
                    return vacio;
                });
    }

    // ==========================================
    // GUARDAR (UPSERT por empleado + período)
    // ==========================================

    @Auditable(accion = "GUARDAR_ASISTENCIA")
    @Transactional
    public void guardar(AsistenciaGuardarDto dto) {

        if (dto.getEmpleadoId() == null
                || dto.getPeriodo() == null
                || dto.getPeriodo().isBlank()) {
            throw new NegocioException(
                    "Empleado y período son obligatorios");
        }

        List<AsistenciaDiaDto> dias =
                dto.getDias() != null ? dto.getDias() : new ArrayList<>();

        validarTiposDia(dias);

        double remun = dto.getRemuneracionBase() != null
                ? dto.getRemuneracionBase()
                : 0.0;

        AsistenciaResumenCalculator.Resumen agregados =
                AsistenciaResumenCalculator.calcular(dias, remun);

        // ----- UPSERT de la cabecera -----
        AsistenciaCabecera cab = cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(
                        dto.getEmpleadoId(), dto.getPeriodo(), 1)
                .orElseGet(() -> {
                    AsistenciaCabecera nueva = new AsistenciaCabecera();
                    nueva.setEmpleadoId(dto.getEmpleadoId());
                    nueva.setPeriodo(dto.getPeriodo());
                    nueva.setActivo(1);
                    nueva.setCreatedAt(LocalDateTime.now());
                    return nueva;
                });

        cab.setRemuneracionBase(dto.getRemuneracionBase());
        cab.setDiasLaborados(agregados.getDiasLaborados());
        cab.setDiasFalta(agregados.getDiasFalta());
        cab.setTotalMinTardanza(agregados.getTotalMinTardanza());
        cab.setDescuentoTardanza(agregados.getDescuentoTardanza());
        cab.setDescuentoFalta(agregados.getDescuentoFalta());
        cab.setMinutosSalidaAnticipada(agregados.getMinutosSalidaAnticipada());
        cab.setMarcasIncompletas(agregados.getMarcasIncompletas());
        cab.setObservacion(dto.getObservacion());
        cab.setEstado(normalizarEstado(dto.getEstado()));

        AsistenciaCabecera guardada = cabeceraRepository.save(cab);

        // ----- Reemplazar el detalle -----
        detalleRepository.deleteByCabeceraId(guardada.getId());

        detalleRepository.saveAll(mapearDetalles(guardada.getId(), dias));

        auditoriaContext.setDetalle(
                "Asistencia guardada empleado " + dto.getEmpleadoId()
                        + " período " + dto.getPeriodo()
                        + " (" + dias.size() + " días)");
    }

    /**
     * Persiste asistencia proveniente de importación masiva del marcador.
     */
    @Transactional
    public void guardarImportacion(
            Long empleadoId,
            String periodo,
            double remuneracionBase,
            String baseOrigen,
            String estado,
            Long importacionId,
            List<AsistenciaDiaDto> dias) {

        AsistenciaGuardarDto dto = new AsistenciaGuardarDto();
        dto.setEmpleadoId(empleadoId);
        dto.setPeriodo(periodo);
        dto.setRemuneracionBase(remuneracionBase);
        dto.setEstado(estado);
        dto.setDias(dias);
        dto.setObservacion("Importación marcador ID " + importacionId);

        AsistenciaCabecera cab = cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .orElseGet(() -> {
                    AsistenciaCabecera nueva = new AsistenciaCabecera();
                    nueva.setEmpleadoId(empleadoId);
                    nueva.setPeriodo(periodo);
                    nueva.setActivo(1);
                    nueva.setCreatedAt(LocalDateTime.now());
                    return nueva;
                });

        validarTiposDia(dias);
        AsistenciaResumenCalculator.Resumen agregados =
                AsistenciaResumenCalculator.calcular(dias, remuneracionBase);

        cab.setRemuneracionBase(remuneracionBase);
        cab.setDiasLaborados(agregados.getDiasLaborados());
        cab.setDiasFalta(agregados.getDiasFalta());
        cab.setTotalMinTardanza(agregados.getTotalMinTardanza());
        cab.setDescuentoTardanza(agregados.getDescuentoTardanza());
        cab.setDescuentoFalta(agregados.getDescuentoFalta());
        cab.setMinutosSalidaAnticipada(agregados.getMinutosSalidaAnticipada());
        cab.setMarcasIncompletas(agregados.getMarcasIncompletas());
        cab.setBaseAsistenciaOrigen(baseOrigen);
        cab.setImportacionId(importacionId);
        cab.setEstado(normalizarEstado(estado));

        AsistenciaCabecera guardada = cabeceraRepository.save(cab);
        detalleRepository.deleteByCabeceraId(guardada.getId());
        detalleRepository.saveAll(mapearDetalles(guardada.getId(), dias));
    }

    void validarTiposDia(List<AsistenciaDiaDto> dias) {
        for (AsistenciaDiaDto d : dias) {
            String tipo = d.getTipoDia();
            if (tipo == null || !TIPOS_DIA.contains(tipo)) {
                throw new NegocioException("Tipo de día inválido: " + tipo);
            }
        }
    }

    String normalizarEstado(String estado) {
        if (estado != null && ESTADOS_CABECERA.contains(estado)) {
            return estado;
        }
        return "BORRADOR";
    }

    /**
     * ROUND((remuneracion / 30 / 8 / 60) * minutos, 2) — expuesto para tests.
     */
    double calcularDescuentoTardanza(double remuneracion, int minutos) {
        return AsistenciaResumenCalculator.calcularDescuentoTardanza(remuneracion, minutos);
    }

    /** ROUND((remuneracion / 30) * dias_falta, 2) — expuesto para tests. */
    double calcularDescuentoFalta(double remuneracion, int diasFalta) {
        return AsistenciaResumenCalculator.calcularDescuentoFalta(remuneracion, diasFalta);
    }

    private List<AsistenciaDetalle> mapearDetalles(Long cabeceraId, List<AsistenciaDiaDto> dias) {
        List<AsistenciaDetalle> detalles = new ArrayList<>();
        for (AsistenciaDiaDto d : dias) {
            AsistenciaDetalle det = new AsistenciaDetalle();
            det.setCabeceraId(cabeceraId);
            det.setDia(d.getDia());
            det.setTipoDia(d.getTipoDia());
            det.setMinutosTardanza(
                    d.getMinutosTardanza() != null ? Math.max(0, d.getMinutosTardanza()) : 0);
            det.setObservacion(d.getObservacion());
            det.setMarcaEntrada(d.getMarcaEntrada());
            det.setMarcaSalida(d.getMarcaSalida());
            det.setHoraEntradaEsperada(d.getHoraEntradaEsperada());
            det.setMinutosSalidaAnticipada(
                    d.getMinutosSalidaAnticipada() != null ? d.getMinutosSalidaAnticipada() : 0);
            det.setHorasTrabajadasMin(d.getHorasTrabajadasMin());
            det.setHorasExtra25Min(d.getHorasExtra25Min());
            det.setHorasExtra35Min(d.getHorasExtra35Min());
            det.setHorasExtra100Min(d.getHorasExtra100Min());
            det.setHorasExtraTotalMin(d.getHorasExtraTotalMin());
            det.setDiaSemana(d.getDiaSemana());
            det.setOrigen(d.getOrigen() != null ? d.getOrigen() : "MANUAL");
            detalles.add(det);
        }
        return detalles;
    }

    // ==========================================
    // MAPEO
    // ==========================================

    private AsistenciaResponseDto aResponse(AsistenciaCabecera cab) {

        AsistenciaResponseDto dto = new AsistenciaResponseDto();

        dto.setId(cab.getId());
        dto.setEmpleadoId(cab.getEmpleadoId());
        dto.setPeriodo(cab.getPeriodo());
        dto.setRemuneracionBase(cab.getRemuneracionBase());
        dto.setDiasLaborados(cab.getDiasLaborados());
        dto.setDiasFalta(cab.getDiasFalta());
        dto.setTotalMinTardanza(cab.getTotalMinTardanza());
        dto.setDescuentoTardanza(cab.getDescuentoTardanza());
        dto.setDescuentoFalta(cab.getDescuentoFalta());
        dto.setEstado(cab.getEstado());
        dto.setObservacion(cab.getObservacion());

        List<AsistenciaDiaDto> dias =
                detalleRepository.findByCabeceraIdOrderByDia(cab.getId())
                        .stream()
                        .map(det -> {
                            AsistenciaDiaDto d = new AsistenciaDiaDto();
                            d.setDia(det.getDia());
                            d.setTipoDia(det.getTipoDia());
                            d.setMinutosTardanza(det.getMinutosTardanza());
                            d.setObservacion(det.getObservacion());
                            return d;
                        })
                        .toList();
        dto.setDias(new ArrayList<>(dias));

        return dto;
    }
}
