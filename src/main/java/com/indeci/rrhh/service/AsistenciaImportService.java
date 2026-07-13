package com.indeci.rrhh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.dto.AsistenciaImportConfirmRequest;
import com.indeci.rrhh.dto.AsistenciaImportEmpleadoResumenDto;
import com.indeci.rrhh.dto.AsistenciaImportFilaDetalleDto;
import com.indeci.rrhh.dto.AsistenciaImportFilaErrorDto;
import com.indeci.rrhh.dto.AsistenciaImportResumenDto;
import com.indeci.rrhh.dto.AsistenciaImportHistorialDto;
import com.indeci.rrhh.dto.AsistenciaImportPreviewDto;
import com.indeci.rrhh.dto.AsistenciaValidacionBatchDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvParser;
import com.indeci.rrhh.service.asistencia.AsistenciaLectorRouter;
import com.indeci.rrhh.service.asistencia.FormatoMarcador;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvValidator;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresCsvWriter;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresXlsxWriter;
import com.indeci.rrhh.service.asistencia.AsistenciaImportEstrategia;
import com.indeci.rrhh.service.asistencia.AsistenciaMarcadorMapper;
import com.indeci.rrhh.service.asistencia.AsistenciaResumenCalculator;
import com.indeci.rrhh.service.asistencia.CalendarioLaboralService;
import com.indeci.rrhh.service.asistencia.AsistenciaTiempoUtil;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResult;
import com.indeci.rrhh.service.asistencia.MarcadorCsvRow;
import com.indeci.rrhh.service.asistencia.PapeletaJustificacionResolver;
import com.indeci.rrhh.service.asistencia.TardanzaCalculator;
import com.indeci.rrhh.service.asistencia.TardanzaDescuentoCalculator;
import com.indeci.security.auth.SisrhPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsistenciaImportService {

    private static final String ESTADO_PREVIEW = "BORRADOR_PREVIEW";
    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";
    private static final String ESTADO_PARCIAL = "PARCIAL";
    private static final String ESTADO_ANULADA = "ANULADA";
    private static final int MAX_RESULTADO_JSON_LENGTH = 4000;

    private final AsistenciaLectorRouter lectorRouter;
    private final AsistenciaCsvValidator csvValidator;
    private final PeriodoPlanillaRepository periodoRepository;
    private final AsistenciaCabeceraRepository cabeceraRepository;
    private final AsistenciaImportacionRepository importacionRepository;
    private final AsistenciaImportacionFilaRepository filaRepository;
    private final BaseAsistenciaResolver baseResolver;
    private final AsistenciaService asistenciaService;
    private final AuditoriaContext auditoriaContext;
    private final AsistenciaImportErroresCsvWriter erroresCsvWriter;
    private final AsistenciaImportErroresXlsxWriter erroresXlsxWriter;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final JornadaRegimenRepository jornadaRegimenRepository;
    private final AsistenciaDetalleRepository detalleRepository;
    private final CalendarioLaboralService calendarioService;
    private final PapeletaJustificacionResolver papeletaJustificacionResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public AsistenciaImportPreviewDto preview(String periodo, MultipartFile archivo) {
        // === INSTRUMENTACIÓN TEMPORAL DE DIAGNÓSTICO (VALIDAR) ===
        // Log por fase + stack trace exacto ante cualquier fallo, para depurar la carga
        // de ambos relojes. Buscar "[CARGA-DEBUG]" en la consola del backend.
        String nombreArchivo = archivo != null ? archivo.getOriginalFilename() : "(sin nombre)";
        log.info("[CARGA-DEBUG] ===== INICIO VALIDAR: periodo={} archivo={} =====", periodo, nombreArchivo);
        try {
            validarPeriodo(periodo);
            descartarBorradoresPrevios(periodo);
            byte[] bytes = leerBytes(archivo);
            log.info("[CARGA-DEBUG] Fase 1 — archivo leído: {} bytes", bytes.length);
            String hash = sha256(bytes);

            // Detecta el formato (Reloj 1 diario vs Reloj 2 / COEN) y elige el lector.
            AsistenciaLectorRouter.ResultadoLectura lectura = lectorRouter.leer(bytes);
            FormatoMarcador formato = lectura.formato();
            AsistenciaCsvParser.ParseResult parseResult = lectura.parseResult();
            log.info("[CARGA-DEBUG] Fase 2 — formato detectado={} encoding={} filasLeidas={}",
                    formato, parseResult.getEncoding(), parseResult.getFilas().size());
            String usuario = usuarioActual();

            AsistenciaImportacion importacion = new AsistenciaImportacion();
            importacion.setPeriodo(periodo);
            importacion.setNombreArchivo(archivo.getOriginalFilename());
            importacion.setHashSha256(hash);
            importacion.setEncoding(parseResult.getEncoding());
            importacion.setUsuario(usuario);
            importacion.setFechaImportacion(LocalDateTime.now());
            importacion.setEstado(ESTADO_PREVIEW);
            inicializarContadoresPreview(importacion, parseResult.getFilas().size());
            importacion = importacionRepository.save(importacion);
            log.info("[CARGA-DEBUG] Fase 3 — importación creada id={}", importacion.getId());

            String ruta = guardarArchivo(bytes, importacion.getId(), archivo.getOriginalFilename());
            importacion.setRutaArchivo(ruta);
            importacionRepository.save(importacion);

            PeriodoPlanilla periodoPlanilla = obtenerPeriodo(periodo);
            List<MarcadorCsvRow> filasValidadas = new ArrayList<>(parseResult.getFilas());
            log.info("[CARGA-DEBUG] Fase 4 — validando {} filas (formato {})...",
                    filasValidadas.size(), formato);
            csvValidator.validarFilas(filasValidadas, periodoPlanilla, formato);
            // Resumen por estado + muestra de las filas con ERROR (motivo exacto).
            Map<String, Long> porEstado = filasValidadas.stream().collect(Collectors.groupingBy(
                    f -> f.getEstadoFila() == null ? "?" : f.getEstadoFila(), Collectors.counting()));
            log.info("[CARGA-DEBUG] Fase 4b — estados tras validar: {}", porEstado);
            filasValidadas.stream()
                    .filter(f -> "ERROR".equals(f.getEstadoFila()))
                    .limit(15)
                    .forEach(f -> log.info(
                            "[CARGA-DEBUG]   ERROR fila#{} fecha={} dni='{}' nombre='{}' -> {}",
                            f.getNumeroFila(), f.getFecha(), f.getDni(), f.getNombre(),
                            String.join(" | ", f.getErrores())));
            log.info("[CARGA-DEBUG] Fase 5 — validación OK, aplicando jornada...");
            aplicarJornada(filasValidadas);

            log.info("[CARGA-DEBUG] Fase 6 — persistiendo {} filas...", filasValidadas.size());
            persistirFilas(importacion.getId(), filasValidadas, hash, usuario);

            log.info("[CARGA-DEBUG] Fase 7 — construyendo preview/resumen (calendario/faltas)...");
            AsistenciaImportPreviewDto preview = construirPreview(importacion, filasValidadas);
            importacion.setFilasValidas(preview.getFilasValidas());
            importacion.setFilasError(preview.getFilasError());
            importacion.setFilasObservadas(preview.getFilasObservadas());
            importacion.setEmpleadosProcesados(preview.getEmpleadosDetectados());
            importacion.setEmpleadosDetectados(preview.getEmpleadosDetectados());
            importacion.setTamanoBytes((long) bytes.length);
            aplicarPeriodoDetectado(importacion, filasValidadas);
            importacion.setResultadoJson(serializePreview(preview));
            importacionRepository.save(importacion);

            preview.setImportacionId(importacion.getId());
            preview.setMensaje("El archivo fue leído correctamente. Revise las observaciones antes de confirmar.");
            log.info("[CARGA-DEBUG] ===== VALIDAR OK: id={} validas={} error={} obs={} empleados={} =====",
                    importacion.getId(), preview.getFilasValidas(), preview.getFilasError(),
                    preview.getFilasObservadas(), preview.getEmpleadosDetectados());
            return preview;
        } catch (RuntimeException e) {
            log.error("[CARGA-DEBUG] *** ERROR EN VALIDAR (periodo={} archivo={}): {} — {} ***",
                    periodo, nombreArchivo, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * P0 fix — a lo sumo un borrador (BORRADOR_PREVIEW) sin confirmar por periodo.
     * Sin esto, cada preview() sucesivo del mismo periodo (p. ej. el mapeo iterativo
     * de alias COEN, que regenera el preview por cada nombre resuelto) deja el intento
     * anterior como fila huerfana: 1000+ filas de INDECI_ASISTENCIA_IMPORTACION_FILA
     * por cada reintento, sin que nadie las use. El borrador nunca tiene una
     * INDECI_ASISTENCIA_CABECERA asociada (esa FK solo se setea en confirmar()),
     * así que descartarlo por completo aquí es seguro.
     */
    private void descartarBorradoresPrevios(String periodo) {
        List<AsistenciaImportacion> previos =
                importacionRepository.findByPeriodoAndEstado(periodo, ESTADO_PREVIEW);
        for (AsistenciaImportacion previo : previos) {
            filaRepository.deleteByImportacionId(previo.getId());
            eliminarArchivoSiExiste(previo.getRutaArchivo());
            importacionRepository.delete(previo);
        }
    }

    private void eliminarArchivoSiExiste(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(ruta));
        } catch (IOException ex) {
            log.warn("No se pudo eliminar el archivo de un borrador de importacion previo: {}", ruta, ex);
        }
    }

    private void inicializarContadoresPreview(AsistenciaImportacion importacion, int filasTotal) {
        importacion.setFilasTotal(filasTotal);
        importacion.setFilasValidas(0);
        importacion.setFilasError(0);
        importacion.setFilasObservadas(0);
        importacion.setEmpleadosProcesados(0);
    }

    @Auditable(accion = "CONFIRMAR_IMPORT_ASISTENCIA")
    @Transactional
    public AsistenciaImportPreviewDto confirmar(Long importacionId, AsistenciaImportConfirmRequest request) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        if (!ESTADO_PREVIEW.equals(importacion.getEstado())) {
            throw new NegocioException("La importación ya fue procesada o no está en vista previa.");
        }

        String periodo = importacion.getPeriodo();
        String motivo = request != null ? request.getMotivoRectificacion() : null;
        boolean autorizado = usuarioAutorizado();
        String autorizadoPor = autorizado ? usuarioActual() : null;

        // R3/R9/R10 — bloqueo por estado de periodo (planilla cerrada/generada).
        validarPeriodoParaConfirmar(periodo, autorizado, motivo);

        AsistenciaImportEstrategia estrategia = AsistenciaImportEstrategia.desde(
                request != null ? request.getEstrategiaConflicto() : null);

        // R1/R2 — solo migran VALIDA/WARN y OBSERVADAS aceptadas expresamente; ERROR nunca.
        List<AsistenciaImportacionFila> filas =
                filaRepository.findByImportacionIdOrderByNumeroFila(importacionId);
        Map<Long, List<AsistenciaImportacionFila>> porEmpleado = filas.stream()
                .filter(f -> f.getEmpleadoId() != null)
                .filter(this::esMigrable)
                .collect(Collectors.groupingBy(AsistenciaImportacionFila::getEmpleadoId));

        if (estrategia.cancelaConConflicto() && hayConflictos(periodo, porEmpleado.keySet())) {
            throw new NegocioException(
                    "Existen empleados con asistencia previa. Elija otra estrategia o cancele.");
        }

        boolean periodoGenerado = "GENERADO".equalsIgnoreCase(estadoPeriodo(periodo));
        // F3 — calendario del período: genera las FALTAS de días laborables sin marca.
        PeriodoPlanilla periodoPlan = obtenerPeriodo(periodo);
        CalendarioLaboralService.Calendario calendario = calendarioDe(periodoPlan);
        int procesados = 0;
        int omitidos = 0;
        int bloqueados = 0;
        for (Map.Entry<Long, List<AsistenciaImportacionFila>> entry : porEmpleado.entrySet()) {
            Long empleadoId = entry.getKey();
            AsistenciaCabecera activa = cabeceraRepository
                    .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                    .orElse(null);

            if (activa != null) {
                if (!estrategia.reemplazaExistente()) {
                    omitidos++;
                    continue;
                }
                // R2 — rectificar una asistencia VALIDADA (consumible por M05) o un periodo
                // GENERADO exige rol autorizado (PLA_APPROVE) + motivo. D2: bloquea solo este empleado.
                boolean requiereAutorizacion =
                        "VALIDADA".equals(activa.getEstado()) || periodoGenerado;
                if (requiereAutorizacion && (!autorizado || isBlank(motivo))) {
                    bloqueados++;
                    continue;
                }
                guardarEmpleadoDesdeImport(importacion, calendario, periodoPlan, empleadoId,
                        entry.getValue(), motivo, usuarioActual(), autorizadoPor);
                procesados++;
            } else {
                guardarEmpleadoDesdeImport(importacion, calendario, periodoPlan, empleadoId,
                        entry.getValue(), null, null, null);
                procesados++;
            }
        }

        boolean parcial = (omitidos + bloqueados) > 0;
        importacion.setEstado(parcial ? ESTADO_PARCIAL : ESTADO_CONFIRMADA);
        importacion.setEstrategiaConflicto(estrategia.name());
        importacion.setEmpleadosProcesados(procesados);
        importacion.setUsuarioConfirmacion(usuarioActual());
        importacion.setFechaConfirmacion(LocalDateTime.now());
        importacionRepository.save(importacion);

        auditoriaContext.setDetalle(
                "Importación asistencia " + importacionId
                        + " período " + periodo
                        + " empleados=" + procesados
                        + " omitidos=" + omitidos
                        + " bloqueados=" + bloqueados);

        AsistenciaImportPreviewDto resultado = new AsistenciaImportPreviewDto();
        resultado.setImportacionId(importacionId);
        resultado.setPeriodo(periodo);
        resultado.setEmpleadosDetectados(procesados);
        resultado.setEstadoImportacion(importacion.getEstado());
        resultado.setMensaje(mensajeConfirmacion(estrategia, procesados, omitidos, bloqueados));
        return resultado;
    }

    /** R3/R9/R10 — valida que el estado del periodo permita confirmar/rectificar. */
    private void validarPeriodoParaConfirmar(String periodo, boolean autorizado, String motivo) {
        String estado = estadoPeriodo(periodo);
        if ("APROBADO".equalsIgnoreCase(estado) || "CERRADO".equalsIgnoreCase(estado)) {
            throw new NegocioException(
                    "El periodo " + periodo + " está " + estado
                            + "; no se permite modificar la asistencia.");
        }
        if ("GENERADO".equalsIgnoreCase(estado) && (!autorizado || isBlank(motivo))) {
            throw new NegocioException(
                    "El periodo " + periodo + " está GENERADO: la rectificación requiere"
                            + " un usuario con rol autorizado (PLA_APPROVE) y un motivo.");
        }
    }

    private String estadoPeriodo(String periodo) {
        return obtenerPeriodo(periodo).getEstado();
    }

    /** R1/R2 — una fila migra a la asistencia final solo si no es ERROR y, si es OBSERVADA, fue aceptada. */
    private boolean esMigrable(AsistenciaImportacionFila fila) {
        String estado = fila.getEstadoFila();
        if ("ERROR".equals(estado)) {
            return false;
        }
        if ("OBSERVADA".equals(estado)) {
            return fila.getAceptadaObservada() != null && fila.getAceptadaObservada() == 1;
        }
        return true; // VALIDA, WARN
    }

    /** R3 (ajuste #3) — autorización por rol del usuario autenticado, no por flag del frontend. */
    private boolean usuarioAutorizado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a)
                        || SisrhPermission.PLA_APPROVE.equals(a));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** P3 — aceptación expresa de filas OBSERVADAS (usuario, fecha, motivo obligatorio). */
    @Auditable(accion = "ACEPTAR_OBSERVADAS_IMPORT_ASISTENCIA")
    @Transactional
    public int aceptarObservadas(Long importacionId, List<Long> idsFilas, String motivo) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        if (isBlank(motivo)) {
            throw new NegocioException("Debe indicar el motivo de aceptación de las filas observadas.");
        }
        if (!ESTADO_PREVIEW.equals(importacion.getEstado())) {
            throw new NegocioException(
                    "Solo se pueden aceptar observadas mientras la importación está en vista previa.");
        }
        List<AsistenciaImportacionFila> filas =
                filaRepository.findByImportacionIdOrderByNumeroFila(importacionId);
        Set<Long> filtro = (idsFilas != null && !idsFilas.isEmpty())
                ? new HashSet<>(idsFilas) : null;
        String usuario = usuarioActual();
        int aceptadas = 0;
        for (AsistenciaImportacionFila f : filas) {
            if (!"OBSERVADA".equals(f.getEstadoFila())) {
                continue;
            }
            if (filtro != null && !filtro.contains(f.getId())) {
                continue;
            }
            f.setAceptadaObservada(1);
            f.setUsuarioAceptaObs(usuario);
            f.setFechaAceptaObs(LocalDateTime.now());
            f.setMotivoAceptaObs(motivo);
            aceptadas++;
        }
        if (aceptadas > 0) {
            filaRepository.saveAll(filas);
        }
        auditoriaContext.setDetalle(
                "Aceptación observadas importación " + importacionId + " filas=" + aceptadas);
        return aceptadas;
    }

    /** P4 — anulación controlada (revierte el versionado si ya estaba confirmada y el periodo sigue ABIERTO). */
    @Auditable(accion = "ANULAR_IMPORT_ASISTENCIA")
    @Transactional
    public AsistenciaImportPreviewDto anular(Long importacionId, String motivo) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        if (isBlank(motivo)) {
            throw new NegocioException("Debe indicar el motivo de la anulación.");
        }
        if (ESTADO_ANULADA.equals(importacion.getEstado())) {
            throw new NegocioException("La importación ya está anulada.");
        }

        if (ESTADO_PREVIEW.equals(importacion.getEstado())) {
            importacion.setEstado(ESTADO_ANULADA);
        } else if (ESTADO_CONFIRMADA.equals(importacion.getEstado())
                || ESTADO_PARCIAL.equals(importacion.getEstado())) {
            String estadoPeriodo = estadoPeriodo(importacion.getPeriodo());
            if (!"ABIERTO".equalsIgnoreCase(estadoPeriodo)) {
                throw new NegocioException(
                        "Solo se puede anular una importación confirmada si el periodo está ABIERTO."
                                + " Estado actual del periodo: " + estadoPeriodo + ".");
            }
            revertirCabeceras(importacion);
            importacion.setEstado(ESTADO_ANULADA);
        } else {
            throw new NegocioException("La importación no se puede anular en su estado actual.");
        }
        importacionRepository.save(importacion);
        auditoriaContext.setDetalle(
                "Anulación importación " + importacionId + " motivo=" + motivo);

        AsistenciaImportPreviewDto dto = new AsistenciaImportPreviewDto();
        dto.setImportacionId(importacionId);
        dto.setPeriodo(importacion.getPeriodo());
        dto.setEstadoImportacion(importacion.getEstado());
        dto.setMensaje("Importación anulada correctamente.");
        return dto;
    }

    /** Desactiva las cabeceras creadas por la importación y reactiva la versión previa. */
    private void revertirCabeceras(AsistenciaImportacion importacion) {
        List<AsistenciaCabecera> creadas =
                cabeceraRepository.findByImportacionIdAndActivo(importacion.getId(), 1);
        for (AsistenciaCabecera cab : creadas) {
            cab.setActivo(0);
            cabeceraRepository.saveAndFlush(cab); // desactiva antes de reactivar la previa
            cabeceraRepository
                    .findByEmpleadoIdAndPeriodoOrderByVersionDesc(cab.getEmpleadoId(), cab.getPeriodo())
                    .stream()
                    .filter(c -> !c.getId().equals(cab.getId()))
                    .filter(c -> c.getActivo() != null && c.getActivo() == 0)
                    .findFirst()
                    .ifPresent(prev -> {
                        prev.setActivo(1);
                        cabeceraRepository.saveAndFlush(prev);
                    });
        }
    }

    @Transactional(readOnly = true)
    public Page<AsistenciaImportHistorialDto> historial(String periodo, Pageable pageable) {
        Page<AsistenciaImportacion> page = periodo != null && !periodo.isBlank()
                ? importacionRepository.findByPeriodoOrderByFechaImportacionDesc(periodo, pageable)
                : importacionRepository.findAllByOrderByFechaImportacionDesc(pageable);
        return page.map(this::toHistorialDto);
    }

    @Transactional(readOnly = true)
    public AsistenciaImportPreviewDto detalle(Long importacionId) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        if (importacion.getResultadoJson() != null) {
            try {
                return objectMapper.readValue(importacion.getResultadoJson(), AsistenciaImportPreviewDto.class);
            } catch (JsonProcessingException ex) {
                throw new NegocioException("No se pudo leer el detalle de la importación.");
            }
        }
        List<AsistenciaImportacionFila> filas =
                filaRepository.findByImportacionIdOrderByNumeroFila(importacionId);
        List<MarcadorCsvRow> rows = filas.stream().map(this::toMarcadorRow).toList();
        AsistenciaImportPreviewDto preview = construirPreview(importacion, rows);
        preview.setImportacionId(importacionId);
        return preview;
    }

    /** F2 — Detalle paginado server-side con filtros (req 11/12, P8). */
    @Transactional(readOnly = true)
    public Page<AsistenciaImportFilaDetalleDto> detalles(
            Long importacionId,
            String dni,
            String nombre,
            String estado,
            boolean soloErrores,
            Pageable pageable) {
        importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        return filaRepository.buscarDetalle(
                        importacionId,
                        limpiar(dni),
                        limpiar(nombre),
                        limpiar(estado),
                        soloErrores,
                        pageable)
                .map(this::toDetalleDto);
    }

    /** F2 — Resumen liviano de la importación (banda de estado del paso "Validar"). */
    @Transactional(readOnly = true)
    public AsistenciaImportResumenDto resumen(Long importacionId) {
        AsistenciaImportacion imp = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        AsistenciaImportResumenDto dto = new AsistenciaImportResumenDto();
        dto.setImportacionId(imp.getId());
        dto.setNombreArchivo(imp.getNombreArchivo());
        dto.setPeriodo(imp.getPeriodo());
        dto.setPeriodoDetectadoIni(imp.getPeriodoDetectadoIni());
        dto.setPeriodoDetectadoFin(imp.getPeriodoDetectadoFin());
        dto.setFilasLeidas(valor(imp.getFilasTotal()));
        dto.setFilasValidas(valor(imp.getFilasValidas()));
        dto.setFilasObservadas(valor(imp.getFilasObservadas()));
        dto.setFilasError(valor(imp.getFilasError()));
        dto.setEmpleadosDetectados(valor(
                imp.getEmpleadosDetectados() != null && imp.getEmpleadosDetectados() > 0
                        ? imp.getEmpleadosDetectados()
                        : imp.getEmpleadosProcesados()));
        dto.setEstado(imp.getEstado());
        dto.setHashArchivo(imp.getHashSha256());
        dto.setTamanoBytes(imp.getTamanoBytes());
        dto.setDuplicadoHashPrevio(
                imp.getHashSha256() != null
                        && importacionRepository.existsByHashSha256AndIdNot(
                                imp.getHashSha256(), imp.getId()));
        dto.setUsuario(imp.getUsuario());
        dto.setFechaImportacion(imp.getFechaImportacion());
        dto.setUsuarioValidacion(imp.getUsuarioValidacion());
        dto.setFechaValidacion(imp.getFechaValidacion());
        dto.setUsuarioConfirmacion(imp.getUsuarioConfirmacion());
        dto.setFechaConfirmacion(imp.getFechaConfirmacion());
        return dto;
    }

    private AsistenciaImportFilaDetalleDto toDetalleDto(AsistenciaImportacionFila f) {
        AsistenciaImportFilaDetalleDto dto = new AsistenciaImportFilaDetalleDto();
        dto.setId(f.getId());
        dto.setNumeroFila(f.getNumeroFila());
        dto.setEmpleadoId(f.getEmpleadoId());
        dto.setEstado(f.getEstadoFila());
        dto.setDni(f.getDni());
        dto.setEmpleadoSistema(f.getNombreSistema());
        dto.setNombreCsv(f.getNombreCsv());
        dto.setFecha(f.getFecha());
        dto.setDia(f.getDiaSemana());
        dto.setEntradaProg(f.getEntradaProg());
        dto.setSalidaProg(f.getSalidaProg());
        dto.setMarca1(f.getMarca1());
        dto.setMarca2(f.getMarca2());
        dto.setMarca3(f.getMarca3());
        dto.setMarca4(f.getMarca4());
        dto.setTardanzaMin(f.getTardanzaMin());
        dto.setRefrigerioMin(f.getRefrigerioMin());
        dto.setExcesoRefrigMin(f.getExcesoRefrigMin());
        dto.setTiempoRefrigMin(f.getTiempoRefrigMin());
        dto.setTiempoAntesSalMin(f.getTiempoAntesSalMin());
        dto.setHorasTrabMin(f.getHorasTrabMin());
        dto.setHorasExtra25Min(f.getHorasExtra25Min());
        dto.setHorasExtra35Min(f.getHorasExtra35Min());
        dto.setHorasExtra100Min(f.getHorasExtra100Min());
        dto.setHorasExtraTotalMin(f.getHorasExtraTotalMin());
        dto.setObservaciones(f.getObservacionMarcador());
        dto.setMensajeValidacion(f.getMensajeValidacion());
        dto.setAceptadaObservada(f.getAceptadaObservada() != null && f.getAceptadaObservada() == 1);
        return dto;
    }

    private void aplicarPeriodoDetectado(AsistenciaImportacion imp, List<MarcadorCsvRow> filas) {
        LocalDate min = null;
        LocalDate max = null;
        for (MarcadorCsvRow f : filas) {
            LocalDate fecha = f.getFecha();
            if (fecha == null) {
                continue;
            }
            if (min == null || fecha.isBefore(min)) {
                min = fecha;
            }
            if (max == null || fecha.isAfter(max)) {
                max = fecha;
            }
        }
        imp.setPeriodoDetectadoIni(min);
        imp.setPeriodoDetectadoFin(max);
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    @Transactional(readOnly = true)
    public byte[] erroresCsv(Long importacionId) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        List<AsistenciaImportacionFila> filas =
                filaRepository.findByImportacionIdOrderByNumeroFila(importacionId);
        return erroresCsvWriter.generar(importacion, filas);
    }

    /** F4 — Exportación XLSX de filas con error/observadas (req 15). */
    @Transactional(readOnly = true)
    public byte[] erroresXlsx(Long importacionId) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        List<AsistenciaImportacionFila> filas =
                filaRepository.findByImportacionIdOrderByNumeroFila(importacionId);
        return erroresXlsxWriter.generar(importacion, filas);
    }

    @Auditable(accion = "VALIDAR_IMPORT_ASISTENCIA_BATCH")
    @Transactional
    public AsistenciaValidacionBatchDto validarCabeceras(Long importacionId) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        List<AsistenciaCabecera> cabeceras =
                cabeceraRepository.findByImportacionIdAndActivo(importacionId, 1);

        int validadas = 0;
        int observadas = 0;
        int yaValidadas = 0;
        int omitidas = 0;

        for (AsistenciaCabecera cabecera : cabeceras) {
            String estado = cabecera.getEstado();
            if ("OBSERVADA".equals(estado)) {
                observadas++;
                continue;
            }
            if ("VALIDADA".equals(estado)) {
                // Re-validar: recalcula con la jornada vigente (idempotente).
                recalcularTardanzaDesdeMarcas(cabecera);
                refrescarDescuentos(cabecera);
                yaValidadas++;
                continue;
            }
            if ("PREVALIDADA".equals(estado) || "LISTA_PARA_VALIDAR".equals(estado)) {
                cabecera.setEstado("VALIDADA");
                // Punto autoritativo: recalcula la tardanza de cada día desde las marcas
                // reales y la jornada vigente del régimen, y refresca el descuento con la
                // base vigente, ANTES de habilitar la cabecera para el motor M05.
                recalcularTardanzaDesdeMarcas(cabecera);
                refrescarDescuentos(cabecera);
                validadas++;
                continue;
            }
            omitidas++;
        }

        if (validadas > 0 || yaValidadas > 0) {
            cabeceraRepository.saveAll(cabeceras);
        }

        importacion.setUsuarioValidacion(usuarioActual());
        importacion.setFechaValidacion(LocalDateTime.now());
        importacionRepository.save(importacion);

        auditoriaContext.setDetalle(
                "Validación batch asistencia importación " + importacionId
                        + " período " + importacion.getPeriodo()
                        + " validadas=" + validadas);

        AsistenciaValidacionBatchDto dto = new AsistenciaValidacionBatchDto();
        dto.setImportacionId(importacionId);
        dto.setPeriodo(importacion.getPeriodo());
        dto.setTotalCabeceras(cabeceras.size());
        dto.setValidadas(validadas);
        dto.setOmitidas(omitidas);
        dto.setObservadas(observadas);
        dto.setYaValidadas(yaValidadas);
        return dto;
    }

    /**
     * Recalcula descuentoTardanza/Falta de la cabecera con la base vigente del empleado
     * (misma fórmula legal de {@link AsistenciaResumenCalculator}, sin ajustes). No
     * necesita el detalle: usa los agregados ya guardados (totalMinTardanza, diasFalta).
     * Si la base no se puede determinar (&le; 0), no toca los montos.
     */
    /**
     * Recalcula la asistencia de un empleado/periodo (tardanza desde marcas + descuentos)
     * sin pasar por "Validar cabeceras". Disparado desde el botón "Recalcular".
     */
    @Auditable(accion = "RECALCULAR_ASISTENCIA")
    @Transactional
    public void recalcularAsistencia(Long empleadoId, String periodo) {
        AsistenciaCabecera cabecera = cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .orElseThrow(() -> new NegocioException(
                        "No hay asistencia registrada para el empleado en este periodo."));
        recalcularTardanzaDesdeMarcas(cabecera);
        refrescarDescuentos(cabecera);
        cabeceraRepository.save(cabecera);
    }

    /**
     * Recalcula la tardanza de cada día de la cabecera desde las marcas reales
     * (Marca1 ingreso, Marca3 regreso de refrigerio) y la jornada vigente del
     * régimen del empleado. Actualiza el detalle (minutos + tipo de día) y el
     * total de la cabecera. Si el empleado no tiene jornada configurada, NO toca
     * nada y deja una advertencia (el "fallback a 0" deja de ser silencioso).
     */
    private void recalcularTardanzaDesdeMarcas(AsistenciaCabecera cabecera) {
        JornadaRegimen jornada = jornadaDeEmpleado(cabecera.getEmpleadoId());
        if (jornada == null) {
            log.warn("Asistencia empleado {} periodo {}: sin jornada configurada para su régimen; "
                    + "tardanza NO recalculada (se conserva la del marcador).",
                    cabecera.getEmpleadoId(), cabecera.getPeriodo());
            return;
        }
        List<AsistenciaDetalle> detalles =
                detalleRepository.findByCabeceraIdOrderByDia(cabecera.getId());
        // Modelo de dos niveles (V010_95): tardanza diaria EN BRUTO (sin restar
        // tolerancia); el umbral diario clasifica D1 (diario) vs D2 (mensual).
        List<Integer> tardanzasDiarias = new ArrayList<>();
        for (AsistenciaDetalle d : detalles) {
            if (esDiaTrabajado(d.getTipoDia())) {
                Integer min = TardanzaCalculator.calcularBruto(d.getMarcaEntrada(), d.getMarca3(), jornada);
                if (min != null) {
                    d.setMinutosTardanza(min);
                    d.setTipoDia(min > 0 ? "TARDANZA" : "LABORAL");
                }
            }
            if ("TARDANZA".equals(d.getTipoDia()) && d.getMinutosTardanza() != null) {
                tardanzasDiarias.add(d.getMinutosTardanza());
            }
        }
        detalleRepository.saveAll(detalles);

        int umbral = jornada.getUmbralTardanzaDiariaMin() != null ? jornada.getUmbralTardanzaDiariaMin() : 10;
        int tope = jornada.getTopeTardanzaMensualMin() != null ? jornada.getTopeTardanzaMensualMin() : 60;
        TardanzaDescuentoCalculator.Resultado split = TardanzaDescuentoCalculator.calcular(
                tardanzasDiarias, 0, jornada.getJornadaHoras(), umbral, tope);
        cabecera.setMinTardanzaDiaria(split.getMinTardanzaDiaria());
        cabecera.setMinTardanzaMenorAcum(split.getMinTardanzaMenorAcum());
        cabecera.setMinTardanzaExcesoMes(split.getMinTardanzaExcesoMes());
        cabecera.setTotalMinTardanza(split.getMinTardanzaDiaria() + split.getMinTardanzaMenorAcum());
    }

    /** Solo los días efectivamente trabajados se recalculan (no FALTA/DESCANSO/LICENCIA/etc). */
    private boolean esDiaTrabajado(String tipoDia) {
        return "LABORAL".equals(tipoDia) || "TARDANZA".equals(tipoDia);
    }

    /** Jornada vigente del empleado vía su régimen en INDECI_EMPLEADO_PLANILLA. */
    private JornadaRegimen jornadaDeEmpleado(Long empleadoId) {
        if (empleadoId == null) {
            return null;
        }
        Long regimenId = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .orElse(null);
        if (regimenId == null) {
            return null;
        }
        return jornadaRegimenRepository.findByRegimenLaboralId(regimenId).orElse(null);
    }

    private void refrescarDescuentos(AsistenciaCabecera cabecera) {
        BaseAsistenciaResult base = baseResolver.resolver(cabecera.getEmpleadoId());
        if (base == null || base.getRemuneracionBase() <= 0) {
            return;
        }
        double b = base.getRemuneracionBase();
        int diasFalta = cabecera.getDiasFalta() != null ? cabecera.getDiasFalta() : 0;

        // Modelo de dos niveles (V010_95): el descuento usa el split persistido por
        // recalcularTardanzaDesdeMarcas y la tasa por minuto del régimen (divisor
        // remun/30/jornada/60). DESCUENTO_TARDANZA sigue siendo el TOTAL (D1+D2),
        // que el motor de planilla lee sin cambios.
        JornadaRegimen jornada = jornadaDeEmpleado(cabecera.getEmpleadoId());
        java.math.BigDecimal jornadaHoras = (jornada != null && jornada.getJornadaHoras() != null)
                ? jornada.getJornadaHoras()
                : java.math.BigDecimal.valueOf(8);
        int minDiaria = cabecera.getMinTardanzaDiaria() != null ? cabecera.getMinTardanzaDiaria() : 0;
        int minExceso = cabecera.getMinTardanzaExcesoMes() != null ? cabecera.getMinTardanzaExcesoMes() : 0;

        double descDiaria = TardanzaDescuentoCalculator.descuento(b, jornadaHoras, minDiaria);
        double descMensual = TardanzaDescuentoCalculator.descuento(b, jornadaHoras, minExceso);

        cabecera.setRemuneracionBase(b);
        cabecera.setDescuentoTardanzaDiaria(descDiaria);
        cabecera.setDescuentoTardanzaMensual(descMensual);
        cabecera.setDescuentoTardanza(round2(descDiaria + descMensual));
        cabecera.setDescuentoFalta(
                AsistenciaResumenCalculator.calcularDescuentoFalta(b, diasFalta));
    }

    private static double round2(double v) {
        return java.math.BigDecimal.valueOf(v)
                .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private void guardarEmpleadoDesdeImport(
            AsistenciaImportacion importacion,
            CalendarioLaboralService.Calendario calendario,
            PeriodoPlanilla periodoPlan,
            Long empleadoId,
            List<AsistenciaImportacionFila> filasEmpleado,
            String motivoRectificacion,
            String usuarioRectificacion,
            String autorizadoPor) {

        List<AsistenciaDiaDto> diasMarcados = filasEmpleado.stream()
                .sorted(Comparator.comparing(AsistenciaImportacionFila::getFecha))
                .map(this::toDiaDto)
                .toList();
        // F3 — completa con las FALTAS de días laborables sin marca (persiste como detalle).
        List<AsistenciaDiaDto> dias = conFaltasCalendario(
                empleadoId, calendario,
                periodoPlan != null ? periodoPlan.getFechaInicio() : null,
                periodoPlan != null ? periodoPlan.getFechaFin() : null,
                diasMarcados);

        BaseAsistenciaResult base = baseResolver.resolver(empleadoId);
        String estadoCabecera = resolverEstadoCabecera(filasEmpleado, dias);

        asistenciaService.guardarImportacion(
                empleadoId,
                importacion.getPeriodo(),
                base.getRemuneracionBase(),
                base.getOrigen(),
                estadoCabecera,
                importacion.getId(),
                dias,
                motivoRectificacion,
                usuarioRectificacion,
                autorizadoPor);
    }

    private String resolverEstadoCabecera(
            List<AsistenciaImportacionFila> filasEmpleado,
            List<AsistenciaDiaDto> dias) {
        boolean tieneError = filasEmpleado.stream().anyMatch(f -> "ERROR".equals(f.getEstadoFila()));
        if (tieneError) {
            return "OBSERVADA";
        }
        boolean tieneObservado = dias.stream().anyMatch(d -> "OBSERVADO".equals(d.getTipoDia()));
        boolean tieneWarn = filasEmpleado.stream().anyMatch(f -> "WARN".equals(f.getEstadoFila()));
        if (tieneObservado || tieneWarn) {
            return "LISTA_PARA_VALIDAR";
        }
        return "PREVALIDADA";
    }

    /** Calendario del período; null si el período no tiene fechas (no se generan faltas). */
    private CalendarioLaboralService.Calendario calendarioDe(PeriodoPlanilla periodoPlan) {
        if (periodoPlan == null
                || periodoPlan.getFechaInicio() == null
                || periodoPlan.getFechaFin() == null) {
            return null;
        }
        return calendarioService.paraPeriodo(periodoPlan.getFechaInicio(), periodoPlan.getFechaFin());
    }

    /**
     * F3 — Agrega los días laborables SIN marca del período como FALTA (respeta
     * feriados/descansos/vínculo vigente). Si el régimen no es afecto a la planilla
     * INDECI (SPEC D12) o no hay calendario/fechas, devuelve los días tal cual.
     */
    private List<AsistenciaDiaDto> conFaltasCalendario(
            Long empleadoId,
            CalendarioLaboralService.Calendario calendario,
            LocalDate inicio,
            LocalDate fin,
            List<AsistenciaDiaDto> dias) {

        if (calendario == null || inicio == null || fin == null) {
            return dias;
        }
        EmpleadoPlanilla vinculo = empleadoPlanillaRepository
                .findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .orElse(null);
        Long regimenId = vinculo != null ? vinculo.getRegimenLaboralId() : null;
        if (!esAfectoPlanilla(regimenId)) {
            return dias;
        }
        LocalDate ingreso = vinculo != null ? vinculo.getFechaIngreso() : null;
        LocalDate cese = vinculo != null ? vinculo.getFechaCese() : null;
        Set<LocalDate> presentes = dias.stream()
                .map(AsistenciaDiaDto::getDia)
                .filter(f -> f != null)
                .collect(Collectors.toSet());

        List<LocalDate> faltas = calendario.diasFalta(inicio, fin, ingreso, cese, regimenId, presentes);
        if (faltas.isEmpty()) {
            return dias;
        }
        // Conciliación: un día laborable sin marca (o una observada dejada pasar) que
        // esté cubierto por una papeleta APROBADA que justifica asistencia (con goce /
        // teletrabajo) NO se marca FALTA → no descuenta. Papeletas cargadas una sola vez.
        List<com.indeci.rrhh.entity.SolicitudRrhh> justificantes =
                papeletaJustificacionResolver.cargarJustificantes(empleadoId, fin);
        List<AsistenciaDiaDto> resultado = new ArrayList<>(dias);
        for (LocalDate fecha : faltas) {
            resultado.add(papeletaJustificacionResolver.justificar(fecha, justificantes)
                    .orElseGet(() -> diaFalta(fecha)));
        }
        return resultado;
    }

    private AsistenciaDiaDto diaFalta(LocalDate fecha) {
        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setDia(fecha);
        dia.setTipoDia("FALTA");
        dia.setMinutosTardanza(0);
        dia.setObservacion("Falta (día laborable sin marca) — generada por calendario.");
        dia.setOrigen("CALENDARIO");
        return dia;
    }

    /**
     * SPEC D12 (hook) — regímenes NO afectos a la planilla INDECI (ej. destacado
     * militar, pagado por otra entidad) no generan faltas/descuentos. Hoy todos
     * afectan; pendiente cablear el flag por régimen/situación laboral.
     */
    private boolean esAfectoPlanilla(Long regimenLaboralId) {
        return true;
    }

    private AsistenciaDiaDto toDiaDto(AsistenciaImportacionFila fila) {
        Map<String, Object> metadatos = leerMetadatosFila(fila.getErroresJson());
        int minTard = fila.getTardanzaMin() != null
                ? fila.getTardanzaMin()
                : AsistenciaTiempoUtil.toMinutos(fila.getTardanzaRaw());
        int minSat = AsistenciaTiempoUtil.toMinutos(
                metadatos.getOrDefault("salidaAnticipada", "").toString());
        return AsistenciaMarcadorMapper.toDia(
                null,
                fila.getFecha(),
                fila.getMarca1(),
                fila.getMarca2(),
                fila.getMarca3(),
                fila.getMarca4(),
                null,
                minTard,
                minSat,
                fila.getObservacionMarcador());
    }

    private MarcadorCsvRow toMarcadorRow(AsistenciaImportacionFila fila) {
        Map<String, Object> metadatos = leerMetadatosFila(fila.getErroresJson());
        MarcadorCsvRow row = new MarcadorCsvRow();
        row.setNumeroFila(fila.getNumeroFila());
        row.setLineaOriginal(fila.getLineaOriginal());
        row.setDni(fila.getDni());
        row.setFecha(fila.getFecha());
        row.setMarca1(fila.getMarca1());
        row.setMarca2(fila.getMarca2());
        row.setTardanza(fila.getTardanzaRaw());
        row.setSalidaAnticipada(metadatos.getOrDefault("salidaAnticipada", "").toString());
        row.setObservacion(fila.getObservacionMarcador());
        row.setEstadoFila(fila.getEstadoFila());
        row.setEmpleadoId(fila.getEmpleadoId());
        leerLista(metadatos, "errores").forEach(row.getErrores()::add);
        leerLista(metadatos, "advertencias").forEach(row.getAdvertencias()::add);
        return row;
    }

    /**
     * F-B — Recalcula la tardanza de cada fila desde las marcas y la jornada del régimen
     * del empleado (ingreso + regreso de almuerzo, restando tolerancias). Si no hay jornada
     * configurada o no hay marcas comparables, deja {@code null} → se usa el valor del reloj.
     */
    private void aplicarJornada(List<MarcadorCsvRow> filas) {
        Map<Long, JornadaRegimen> jornadaPorEmpleado = new HashMap<>();
        Map<Long, JornadaRegimen> jornadaPorRegimen = new HashMap<>();
        for (MarcadorCsvRow row : filas) {
            Long empleadoId = row.getEmpleadoId();
            if (empleadoId == null) {
                continue;
            }
            if (!jornadaPorEmpleado.containsKey(empleadoId)) {
                jornadaPorEmpleado.put(empleadoId, resolverJornada(empleadoId, jornadaPorRegimen));
            }
            row.setTardanzaMinCalculada(
                    TardanzaCalculator.calcular(row, jornadaPorEmpleado.get(empleadoId)));
        }
    }

    private JornadaRegimen resolverJornada(Long empleadoId, Map<Long, JornadaRegimen> cachePorRegimen) {
        Long regimenId = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .orElse(null);
        if (regimenId == null) {
            return null;
        }
        if (!cachePorRegimen.containsKey(regimenId)) {
            cachePorRegimen.put(regimenId,
                    jornadaRegimenRepository.findByRegimenLaboralId(regimenId).orElse(null));
        }
        return cachePorRegimen.get(regimenId);
    }

    /** Tardanza efectiva: la recalculada por jornada o, si no hay, el valor del reloj. */
    private int tardanzaMinFinal(MarcadorCsvRow row) {
        return row.getTardanzaMinCalculada() != null
                ? row.getTardanzaMinCalculada()
                : AsistenciaTiempoUtil.toMinutos(row.getTardanza());
    }

    private void persistirFilas(
            Long importacionId,
            List<MarcadorCsvRow> filas,
            String hash,
            String usuario) {
        List<AsistenciaImportacionFila> entities = new ArrayList<>();
        for (MarcadorCsvRow row : filas) {
            AsistenciaImportacionFila fila = new AsistenciaImportacionFila();
            fila.setImportacionId(importacionId);
            fila.setNumeroFila(row.getNumeroFila());
            // No persistir la línea cruda del CSV en filas VALIDA: evita duplicar
            // hasta 4000 chars por fila en la mayoría de los casos (crecimiento
            // desproporcionado del tablespace). Se conserva solo donde sirve para
            // depurar (WARN/ERROR/OBSERVADA).
            if (!"VALIDA".equals(row.getEstadoFila())) {
                fila.setLineaOriginal(truncar(row.getLineaOriginal(), 4000));
            }
            fila.setDni(row.getDni());
            fila.setNombreCsv(truncar(row.getNombre(), 150));
            fila.setNombreSistema(truncar(row.getNombreSistema(), 150));
            fila.setDiaSemana(truncar(row.getDiaSemana(), 12));
            fila.setFecha(row.getFecha());
            fila.setEntradaProg(truncar(row.getHoraEntradaEsperada(), 8));
            fila.setSalidaProg(truncar(row.getSalidaProgramada(), 8));
            fila.setMarca1(row.getMarca1());
            fila.setMarca2(row.getMarca2());
            fila.setMarca3(row.getMarca3());
            fila.setMarca4(row.getMarca4());
            fila.setTardanzaRaw(row.getTardanza());
            fila.setTardanzaMin(tardanzaMinFinal(row));
            fila.setRefrigerioMin(AsistenciaTiempoUtil.toMinutos(row.getRefrigerio()));
            fila.setExcesoRefrigMin(AsistenciaTiempoUtil.toMinutos(row.getExcesoRefrigerio()));
            fila.setTiempoRefrigMin(AsistenciaTiempoUtil.toMinutos(row.getTiempoRefrigerio()));
            fila.setTiempoAntesSalMin(AsistenciaTiempoUtil.toMinutos(row.getTiempoAntesSalida()));
            fila.setHorasTrabMin(AsistenciaTiempoUtil.toMinutos(row.getHorasTrabajadas()));
            fila.setHorasExtra25Min(AsistenciaTiempoUtil.toMinutos(row.getHorasExtra25()));
            fila.setHorasExtra35Min(AsistenciaTiempoUtil.toMinutos(row.getHorasExtra35()));
            fila.setHorasExtra100Min(AsistenciaTiempoUtil.toMinutos(row.getHorasExtra100()));
            fila.setHorasExtraTotalMin(AsistenciaTiempoUtil.toMinutos(row.getHorasExtraTotal()));
            fila.setObservacionMarcador(row.getObservacion());
            fila.setMensajeValidacion(truncar(mensajeFila(row), 500));
            fila.setEstadoFila(row.getEstadoFila());
            fila.setAceptadaObservada(0);
            fila.setEmpleadoId(row.getEmpleadoId());
            fila.setHashArchivo(hash);
            fila.setUsuarioImportacion(usuario);
            fila.setFechaImportacion(LocalDateTime.now());
            fila.setErroresJson(serializeErrores(row));
            entities.add(fila);
        }
        filaRepository.saveAll(entities);
    }

    private AsistenciaImportPreviewDto construirPreview(
            AsistenciaImportacion importacion,
            List<MarcadorCsvRow> filas) {

        AsistenciaImportPreviewDto preview = new AsistenciaImportPreviewDto();
        preview.setPeriodo(importacion.getPeriodo());
        preview.setNombreArchivo(importacion.getNombreArchivo());
        preview.setEncoding(importacion.getEncoding());
        preview.setHashArchivo(importacion.getHashSha256());
        preview.setEstadoImportacion(importacion.getEstado());
        preview.setFilasTotal(filas.size());

        int validasLimpias = 0;
        int advertencias = 0;
        int errores = 0;
        int observadas = 0;
        List<AsistenciaImportFilaErrorDto> erroresDto = new ArrayList<>();
        Set<String> dnisConError = new LinkedHashSet<>();

        Map<Long, List<MarcadorCsvRow>> porEmpleado = new LinkedHashMap<>();
        for (MarcadorCsvRow fila : filas) {
            switch (fila.getEstadoFila()) {
                case "ERROR" -> {
                    errores++;
                    registrarDniConError(dnisConError, fila.getDni());
                }
                case "OBSERVADA" -> observadas++;
                case "WARN" -> advertencias++;
                default -> validasLimpias++;
            }
            erroresDto.add(filaPreviewDto(fila));
            if (fila.getEmpleadoId() != null && !"ERROR".equals(fila.getEstadoFila())) {
                porEmpleado.computeIfAbsent(fila.getEmpleadoId(), k -> new ArrayList<>()).add(fila);
            }
        }

        preview.setFilasValidas(validasLimpias + advertencias);
        preview.setFilasValidasLimpias(validasLimpias);
        preview.setFilasAdvertencia(advertencias);
        preview.setFilasError(errores);
        preview.setFilasObservadas(observadas);
        preview.setErrores(erroresDto);
        preview.setEmpleadosDetectados(porEmpleado.size());
        preview.setEmpleadosConError(dnisConError.size());

        if (!porEmpleado.isEmpty()) {
            // F3 — un solo calendario para todo el período (feriados/descansos).
            PeriodoPlanilla periodoPlan = obtenerPeriodo(importacion.getPeriodo());
            CalendarioLaboralService.Calendario calendario = calendarioDe(periodoPlan);
            
            java.util.Set<Long> conConflicto = cabeceraRepository.findEmpleadosIdsConCabeceraActiva(
                    porEmpleado.keySet(), importacion.getPeriodo(), 1);

            for (Map.Entry<Long, List<MarcadorCsvRow>> entry : porEmpleado.entrySet()) {
                boolean tieneConflicto = conConflicto.contains(entry.getKey());
                preview.getEmpleados().add(construirResumenEmpleado(
                        periodoPlan, calendario, entry.getKey(), entry.getValue(), tieneConflicto));
            }
        }
        return preview;
    }

    private void registrarDniConError(Set<String> dnisConError, String dni) {
        if (dni != null && !dni.isBlank()) {
            dnisConError.add(dni);
        }
    }

    private AsistenciaImportEmpleadoResumenDto construirResumenEmpleado(
            PeriodoPlanilla periodoPlan,
            CalendarioLaboralService.Calendario calendario,
            Long empleadoId,
            List<MarcadorCsvRow> filas,
            boolean conflictoExistente) {

        String periodo = periodoPlan.getPeriodo();
        AsistenciaImportEmpleadoResumenDto resumen = new AsistenciaImportEmpleadoResumenDto();
        resumen.setEmpleadoId(empleadoId);
        resumen.setEmpleadoEncontrado(true);
        resumen.setDni(filas.get(0).getDni());
        resumen.setNombreMarcador(filas.get(0).getNombre());
        resumen.setNombreSistema(filas.get(0).getNombreSistema());
        resumen.setConflictoExistente(conflictoExistente);

        List<AsistenciaDiaDto> dias = filas.stream()
                .sorted(Comparator.comparing(MarcadorCsvRow::getFecha))
                .map(f -> AsistenciaMarcadorMapper.toDia(
                        f.getDiaSemana(),
                        f.getFecha(),
                        f.getMarca1(),
                        f.getMarca2(),
                        f.getMarca3(),
                        f.getMarca4(),
                        f.getHoraEntradaEsperada(),
                        tardanzaMinFinal(f),
                        AsistenciaTiempoUtil.toMinutos(f.getSalidaAnticipada()),
                        f.getObservacion()))
                .toList();

        // F3 — completa con las FALTAS de días laborables sin marca para el conteo/descuento.
        List<AsistenciaDiaDto> diasConFaltas = conFaltasCalendario(
                empleadoId, calendario, periodoPlan.getFechaInicio(), periodoPlan.getFechaFin(), dias);

        BaseAsistenciaResult base = baseResolver.resolver(empleadoId);
        AsistenciaResumenCalculator.Resumen calc =
                AsistenciaResumenCalculator.calcular(diasConFaltas, base.getRemuneracionBase());

        resumen.setDiasLaborados(calc.getDiasLaborados());
        resumen.setDiasFalta(calc.getDiasFalta());
        resumen.setTotalMinTardanza(calc.getTotalMinTardanza());
        resumen.setMinutosSalidaAnticipada(calc.getMinutosSalidaAnticipada());
        resumen.setMarcasIncompletas(calc.getMarcasIncompletas());
        resumen.setDiasObservados((int) dias.stream().filter(d -> "OBSERVADO".equals(d.getTipoDia())).count());
        resumen.setRemuneracionBase(base.getRemuneracionBase());
        resumen.setBaseOrigen(base.getOrigen());
        resumen.setDescuentoTardanza(calc.getDescuentoTardanza());
        resumen.setDescuentoFalta(calc.getDescuentoFalta());
        resumen.setDescuentoTotal(calc.getDescuentoTotal());
        resumen.getAdvertencias().addAll(base.getAdvertencias());
        resumen.setEstadoCabeceraPropuesto(
                filas.stream().anyMatch(f -> "ERROR".equals(f.getEstadoFila()))
                        ? "OBSERVADA"
                        : resumen.getDiasObservados() > 0
                                ? "LISTA_PARA_VALIDAR"
                                : "PREVALIDADA");
        return resumen;
    }

    private AsistenciaImportFilaErrorDto filaPreviewDto(MarcadorCsvRow fila) {
        return errorDto(fila, severidadFila(fila), mensajeFila(fila));
    }

    private String severidadFila(MarcadorCsvRow fila) {
        return switch (fila.getEstadoFila()) {
            case "ERROR" -> "ERROR";
            case "WARN" -> "WARN";
            case "OBSERVADA" -> "OBSERVADA";
            default -> "VALIDA";
        };
    }

    private String mensajeFila(MarcadorCsvRow fila) {
        if ("ERROR".equals(fila.getEstadoFila()) && !fila.getErrores().isEmpty()) {
            return String.join(" ", fila.getErrores());
        }
        if ("WARN".equals(fila.getEstadoFila()) && !fila.getAdvertencias().isEmpty()) {
            return String.join(" ", fila.getAdvertencias())
                    + " No bloquea la carga porque la identificacion principal es el DNI.";
        }
        if ("OBSERVADA".equals(fila.getEstadoFila())) {
            return mensajeObservada(fila);
        }
        return "Fila valida: DNI encontrado, empleado activo, fecha dentro del periodo y sin incidencias detectadas.";
    }

    private String mensajeObservada(MarcadorCsvRow fila) {
        String obs = fila.getObservacion() != null ? fila.getObservacion().trim() : "";
        if (!obs.isBlank()) {
            return "Fila observada por el marcador: " + obs + ". Requiere revision de RR. HH.";
        }
        return "Fila observada: no registra marcas de entrada ni salida. Requiere revision de RR. HH.";
    }

    private AsistenciaImportFilaErrorDto errorDto(MarcadorCsvRow fila, String severidad, String mensaje) {
        AsistenciaImportFilaErrorDto dto = new AsistenciaImportFilaErrorDto();
        dto.setLinea(fila.getNumeroFila());
        dto.setDni(fila.getDni());
        dto.setFecha(fila.getFecha() != null ? fila.getFecha().toString() : null);
        dto.setSeveridad(severidad);
        dto.setMensaje(mensaje);
        dto.setContenido(fila.getLineaOriginal());
        return dto;
    }

    private boolean hayConflictos(String periodo, java.util.Set<Long> empleadoIds) {
        for (Long empleadoId : empleadoIds) {
            if (cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private String mensajeConfirmacion(
            AsistenciaImportEstrategia estrategia,
            int procesados,
            int omitidos,
            int bloqueados) {
        String sufijo = "";
        if (bloqueados > 0) {
            sufijo = " " + bloqueados + " empleado(s) quedaron bloqueados por requerir autorización"
                    + " (rol PLA_APPROVE) y motivo de rectificación.";
        }
        if (procesados == 0) {
            return ("No se importó ningún empleado con la estrategia seleccionada." + sufijo).trim();
        }
        if (estrategia == AsistenciaImportEstrategia.REEMPLAZAR_PERIODO_COMPLETO) {
            return ("La asistencia del periodo fue reemplazada para los empleados válidos del archivo." + sufijo).trim();
        }
        if (estrategia == AsistenciaImportEstrategia.REEMPLAZAR_EMPLEADOS_ARCHIVO) {
            return ("La asistencia fue reemplazada para los empleados incluidos en el archivo." + sufijo).trim();
        }
        if (omitidos > 0 || bloqueados > 0) {
            return ("La asistencia fue importada parcialmente; algunos empleados con asistencia previa"
                    + " fueron omitidos o bloqueados." + sufijo).trim();
        }
        return "La asistencia fue importada correctamente.";
    }

    private PeriodoPlanilla obtenerPeriodo(String periodo) {
        return periodoRepository.findByPeriodoAndActivo(periodo, 1)
                .orElseThrow(() -> new NegocioException("Período no encontrado."));
    }

    private void validarPeriodo(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            throw new NegocioException("El período es obligatorio.");
        }
        obtenerPeriodo(periodo);
    }

    private byte[] leerBytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException ex) {
            throw new NegocioException("No se pudo leer el archivo CSV.");
        }
    }

    private String guardarArchivo(byte[] bytes, Long importacionId, String nombreOriginal) {
        try {
            Path dir = Path.of("./data/asistencia/import");
            Files.createDirectories(dir);
            String safeName = nombreOriginal != null
                    ? nombreOriginal.replaceAll("[^a-zA-Z0-9._-]", "_")
                    : "import.csv";
            Path destino = dir.resolve(importacionId + "_" + safeName);
            Files.write(destino, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return destino.toString();
        } catch (IOException ex) {
            throw new NegocioException("No se pudo almacenar el archivo de importación.");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new NegocioException("No se pudo calcular el hash del archivo.");
        }
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "SISTEMA";
    }

    private String serializeErrores(MarcadorCsvRow row) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("errores", row.getErrores());
            payload.put("advertencias", row.getAdvertencias());
            payload.put("salidaAnticipada", row.getSalidaAnticipada() != null ? row.getSalidaAnticipada() : "");
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private Map<String, Object> leerMetadatosFila(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> leerLista(Map<String, Object> metadatos, String clave) {
        Object valor = metadatos.get(clave);
        if (valor instanceof List<?> lista) {
            return lista.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private String serializePreview(AsistenciaImportPreviewDto preview) {
        try {
            String json = objectMapper.writeValueAsString(preview);
            return json.length() <= MAX_RESULTADO_JSON_LENGTH ? json : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private AsistenciaImportHistorialDto toHistorialDto(AsistenciaImportacion importacion) {
        AsistenciaImportHistorialDto dto = new AsistenciaImportHistorialDto();
        dto.setId(importacion.getId());
        dto.setPeriodo(importacion.getPeriodo());
        dto.setNombreArchivo(importacion.getNombreArchivo());
        dto.setUsuario(importacion.getUsuario());
        dto.setFechaImportacion(importacion.getFechaImportacion());
        dto.setEstado(importacion.getEstado());
        dto.setFilasTotal(valor(importacion.getFilasTotal()));
        dto.setFilasValidas(valor(importacion.getFilasValidas()));
        dto.setFilasError(valor(importacion.getFilasError()));
        dto.setEmpleadosProcesados(valor(importacion.getEmpleadosProcesados()));
        dto.setEstadoValidacion(estadoValidacion(importacion));
        return dto;
    }

    /**
     * Estado de validación de una importación confirmada (solo lectura en el Historial):
     * REQUIERE_CALCULO si tiene cabeceras activas sin VALIDAR; VALIDADO si todas validadas;
     * null si no aplica (borrador o anulada).
     */
    private String estadoValidacion(AsistenciaImportacion importacion) {
        String estado = importacion.getEstado();
        if (!ESTADO_CONFIRMADA.equals(estado) && !ESTADO_PARCIAL.equals(estado)) {
            return null;
        }
        long total = cabeceraRepository.countByImportacionIdAndActivo(importacion.getId(), 1);
        if (total == 0) {
            return null;
        }
        long pendientes = cabeceraRepository
                .countByImportacionIdAndActivoAndEstadoNot(importacion.getId(), 1, "VALIDADA");
        return pendientes > 0 ? "REQUIERE_CALCULO" : "VALIDADO";
    }

    private static int valor(Integer n) {
        return n != null ? n : 0;
    }

    private static String truncar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
