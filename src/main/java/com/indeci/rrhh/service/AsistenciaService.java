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

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            "LABORAL", "FALTA", "TARDANZA", "LICENCIA", "VACACIONES", "DESCANSO");

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

        // ----- Recalcular agregados desde el detalle -----
        int diasLaborados = 0;
        int diasFalta = 0;
        int totalMin = 0;

        for (AsistenciaDiaDto d : dias) {
            String tipo = d.getTipoDia();

            if (tipo == null || !TIPOS_DIA.contains(tipo)) {
                throw new NegocioException(
                        "Tipo de día inválido: " + tipo);
            }

            if (TIPOS_LABORADOS.contains(tipo)) {
                diasLaborados++;
            }
            if ("FALTA".equals(tipo)) {
                diasFalta++;
            }
            if ("TARDANZA".equals(tipo)
                    && d.getMinutosTardanza() != null) {
                totalMin += Math.max(0, d.getMinutosTardanza());
            }
        }

        double remun = dto.getRemuneracionBase() != null
                ? dto.getRemuneracionBase()
                : 0.0;

        double descTardanza =
                calcularDescuentoTardanza(remun, totalMin);
        double descFalta =
                calcularDescuentoFalta(remun, diasFalta);

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
        cab.setDiasLaborados(diasLaborados);
        cab.setDiasFalta(diasFalta);
        cab.setTotalMinTardanza(totalMin);
        cab.setDescuentoTardanza(descTardanza);
        cab.setDescuentoFalta(descFalta);
        cab.setObservacion(dto.getObservacion());
        cab.setEstado(
                "VALIDADA".equals(dto.getEstado())
                        ? "VALIDADA"
                        : "BORRADOR");

        AsistenciaCabecera guardada = cabeceraRepository.save(cab);

        // ----- Reemplazar el detalle -----
        detalleRepository.deleteByCabeceraId(guardada.getId());

        List<AsistenciaDetalle> detalles = new ArrayList<>();
        for (AsistenciaDiaDto d : dias) {
            AsistenciaDetalle det = new AsistenciaDetalle();
            det.setCabeceraId(guardada.getId());
            det.setDia(d.getDia());
            det.setTipoDia(d.getTipoDia());
            det.setMinutosTardanza(
                    d.getMinutosTardanza() != null
                            ? Math.max(0, d.getMinutosTardanza())
                            : 0);
            det.setObservacion(d.getObservacion());
            detalles.add(det);
        }
        detalleRepository.saveAll(detalles);

        auditoriaContext.setDetalle(
                "Asistencia guardada empleado " + dto.getEmpleadoId()
                        + " período " + dto.getPeriodo()
                        + " (" + dias.size() + " días)");
    }

    // ==========================================
    // CÁLCULO DESCUENTO — D.Leg. 276 Art. 24 (REGLA 276-02)
    // ==========================================

    /**
     * ROUND((remuneracion / 30 / 8 / 60) * minutos, 2).
     * Se multiplica antes de dividir para redondear una sola vez (sin drift).
     */
    double calcularDescuentoTardanza(double remuneracion, int minutos) {
        if (remuneracion <= 0 || minutos <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(remuneracion)
                .multiply(BigDecimal.valueOf(minutos))
                .divide(BigDecimal.valueOf(30L * 8 * 60), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** ROUND((remuneracion / 30) * dias_falta, 2). */
    double calcularDescuentoFalta(double remuneracion, int diasFalta) {
        if (remuneracion <= 0 || diasFalta <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(remuneracion)
                .multiply(BigDecimal.valueOf(diasFalta))
                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP)
                .doubleValue();
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
