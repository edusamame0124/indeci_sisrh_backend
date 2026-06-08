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
import com.indeci.rrhh.dto.AsistenciaImportFilaErrorDto;
import com.indeci.rrhh.dto.AsistenciaImportHistorialDto;
import com.indeci.rrhh.dto.AsistenciaImportPreviewDto;
import com.indeci.rrhh.dto.AsistenciaValidacionBatchDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvParser;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvValidator;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresCsvWriter;
import com.indeci.rrhh.service.asistencia.AsistenciaImportEstrategia;
import com.indeci.rrhh.service.asistencia.AsistenciaMarcadorMapper;
import com.indeci.rrhh.service.asistencia.AsistenciaResumenCalculator;
import com.indeci.rrhh.service.asistencia.AsistenciaTiempoUtil;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResult;
import com.indeci.rrhh.service.asistencia.MarcadorCsvRow;
import lombok.RequiredArgsConstructor;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AsistenciaImportService {

    private static final String ESTADO_PREVIEW = "BORRADOR_PREVIEW";
    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";
    private static final String ESTADO_PARCIAL = "PARCIAL";
    private static final int MAX_RESULTADO_JSON_LENGTH = 4000;

    private final AsistenciaCsvParser csvParser;
    private final AsistenciaCsvValidator csvValidator;
    private final PeriodoPlanillaRepository periodoRepository;
    private final AsistenciaCabeceraRepository cabeceraRepository;
    private final AsistenciaImportacionRepository importacionRepository;
    private final AsistenciaImportacionFilaRepository filaRepository;
    private final BaseAsistenciaResolver baseResolver;
    private final AsistenciaService asistenciaService;
    private final AuditoriaContext auditoriaContext;
    private final AsistenciaImportErroresCsvWriter erroresCsvWriter;
    private final ObjectMapper objectMapper;

    @Transactional
    public AsistenciaImportPreviewDto preview(String periodo, MultipartFile archivo) {
        validarPeriodo(periodo);
        byte[] bytes = leerBytes(archivo);
        String hash = sha256(bytes);
        AsistenciaCsvParser.ParseResult parseResult = csvParser.parse(bytes);
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

        String ruta = guardarArchivo(bytes, importacion.getId(), archivo.getOriginalFilename());
        importacion.setRutaArchivo(ruta);
        importacionRepository.save(importacion);

        PeriodoPlanilla periodoPlanilla = obtenerPeriodo(periodo);
        List<MarcadorCsvRow> filasValidadas = new ArrayList<>(parseResult.getFilas());
        csvValidator.validarFilas(filasValidadas, periodoPlanilla);

        persistirFilas(importacion.getId(), filasValidadas, hash, usuario);

        AsistenciaImportPreviewDto preview = construirPreview(importacion, filasValidadas);
        importacion.setFilasValidas(preview.getFilasValidas());
        importacion.setFilasError(preview.getFilasError());
        importacion.setFilasObservadas(preview.getFilasObservadas());
        importacion.setEmpleadosProcesados(preview.getEmpleadosDetectados());
        importacion.setResultadoJson(serializePreview(preview));
        importacionRepository.save(importacion);

        preview.setImportacionId(importacion.getId());
        preview.setMensaje("El archivo fue leído correctamente. Revise las observaciones antes de confirmar.");
        return preview;
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

        AsistenciaImportEstrategia estrategia = AsistenciaImportEstrategia.desde(
                request != null ? request.getEstrategiaConflicto() : null);

        List<AsistenciaImportacionFila> filas =
                filaRepository.findByImportacionIdOrderByNumeroFila(importacionId);
        Map<Long, List<AsistenciaImportacionFila>> porEmpleado = filas.stream()
                .filter(f -> f.getEmpleadoId() != null)
                .filter(f -> !"ERROR".equals(f.getEstadoFila()))
                .collect(Collectors.groupingBy(AsistenciaImportacionFila::getEmpleadoId));

        if (estrategia.cancelaConConflicto()
                && hayConflictos(importacion.getPeriodo(), porEmpleado.keySet())) {
            throw new NegocioException(
                    "Existen empleados con asistencia previa. Elija otra estrategia o cancele.");
        }

        int procesados = 0;
        int omitidos = 0;
        for (Map.Entry<Long, List<AsistenciaImportacionFila>> entry : porEmpleado.entrySet()) {
            Long empleadoId = entry.getKey();
            boolean existe = cabeceraRepository
                    .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, importacion.getPeriodo(), 1)
                    .isPresent();
            if (existe && estrategia.omiteExistente()) {
                omitidos++;
                continue;
            }
            if (existe && estrategia.cancelaConConflicto()) {
                omitidos++;
                continue;
            }
            if (existe && !estrategia.reemplazaExistente()) {
                omitidos++;
                continue;
            }
            guardarEmpleadoDesdeImport(importacion, empleadoId, entry.getValue());
            procesados++;
        }

        importacion.setEstado(omitidos > 0 ? ESTADO_PARCIAL : ESTADO_CONFIRMADA);
        importacion.setEstrategiaConflicto(estrategia.name());
        importacion.setEmpleadosProcesados(procesados);
        importacionRepository.save(importacion);

        auditoriaContext.setDetalle(
                "Importación asistencia " + importacionId
                        + " período " + importacion.getPeriodo()
                        + " empleados=" + procesados);

        AsistenciaImportPreviewDto resultado = new AsistenciaImportPreviewDto();
        resultado.setImportacionId(importacionId);
        resultado.setPeriodo(importacion.getPeriodo());
        resultado.setEmpleadosDetectados(procesados);
        resultado.setMensaje(mensajeConfirmacion(estrategia, procesados, omitidos));
        return resultado;
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

    @Transactional(readOnly = true)
    public byte[] erroresCsv(Long importacionId) {
        AsistenciaImportacion importacion = importacionRepository.findById(importacionId)
                .orElseThrow(() -> new NegocioException("Importación no encontrada."));
        List<AsistenciaImportacionFila> filas =
                filaRepository.findByImportacionIdOrderByNumeroFila(importacionId);
        return erroresCsvWriter.generar(importacion, filas);
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
            if ("VALIDADA".equals(estado)) {
                yaValidadas++;
                continue;
            }
            if ("OBSERVADA".equals(estado)) {
                observadas++;
                continue;
            }
            if ("PREVALIDADA".equals(estado) || "LISTA_PARA_VALIDAR".equals(estado)) {
                cabecera.setEstado("VALIDADA");
                validadas++;
                continue;
            }
            omitidas++;
        }

        if (validadas > 0) {
            cabeceraRepository.saveAll(cabeceras);
        }

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

    private void guardarEmpleadoDesdeImport(
            AsistenciaImportacion importacion,
            Long empleadoId,
            List<AsistenciaImportacionFila> filasEmpleado) {

        List<AsistenciaDiaDto> dias = filasEmpleado.stream()
                .sorted(Comparator.comparing(AsistenciaImportacionFila::getFecha))
                .map(this::toDiaDto)
                .toList();

        BaseAsistenciaResult base = baseResolver.resolver(empleadoId);
        String estadoCabecera = resolverEstadoCabecera(filasEmpleado, dias);

        asistenciaService.guardarImportacion(
                empleadoId,
                importacion.getPeriodo(),
                base.getRemuneracionBase(),
                base.getOrigen(),
                estadoCabecera,
                importacion.getId(),
                dias);
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

    private AsistenciaDiaDto toDiaDto(AsistenciaImportacionFila fila) {
        Map<String, Object> metadatos = leerMetadatosFila(fila.getErroresJson());
        int minTard = AsistenciaTiempoUtil.toMinutos(fila.getTardanzaRaw());
        int minSat = AsistenciaTiempoUtil.toMinutos(
                metadatos.getOrDefault("salidaAnticipada", "").toString());
        return AsistenciaMarcadorMapper.toDia(
                null,
                fila.getFecha(),
                fila.getMarca1(),
                fila.getMarca2(),
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
            fila.setLineaOriginal(truncar(row.getLineaOriginal(), 4000));
            fila.setDni(row.getDni());
            fila.setFecha(row.getFecha());
            fila.setMarca1(row.getMarca1());
            fila.setMarca2(row.getMarca2());
            fila.setTardanzaRaw(row.getTardanza());
            fila.setObservacionMarcador(row.getObservacion());
            fila.setEstadoFila(row.getEstadoFila());
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

        for (Map.Entry<Long, List<MarcadorCsvRow>> entry : porEmpleado.entrySet()) {
            preview.getEmpleados().add(construirResumenEmpleado(
                    importacion.getPeriodo(), entry.getKey(), entry.getValue()));
        }
        return preview;
    }

    private void registrarDniConError(Set<String> dnisConError, String dni) {
        if (dni != null && !dni.isBlank()) {
            dnisConError.add(dni);
        }
    }

    private AsistenciaImportEmpleadoResumenDto construirResumenEmpleado(
            String periodo,
            Long empleadoId,
            List<MarcadorCsvRow> filas) {

        AsistenciaImportEmpleadoResumenDto resumen = new AsistenciaImportEmpleadoResumenDto();
        resumen.setEmpleadoId(empleadoId);
        resumen.setEmpleadoEncontrado(true);
        resumen.setDni(filas.get(0).getDni());
        resumen.setNombreMarcador(filas.get(0).getNombre());
        resumen.setNombreSistema(filas.get(0).getNombreSistema());
        resumen.setConflictoExistente(
                cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1).isPresent());

        List<AsistenciaDiaDto> dias = filas.stream()
                .sorted(Comparator.comparing(MarcadorCsvRow::getFecha))
                .map(f -> AsistenciaMarcadorMapper.toDia(
                        f.getDiaSemana(),
                        f.getFecha(),
                        f.getMarca1(),
                        f.getMarca2(),
                        f.getHoraEntradaEsperada(),
                        AsistenciaTiempoUtil.toMinutos(f.getTardanza()),
                        AsistenciaTiempoUtil.toMinutos(f.getSalidaAnticipada()),
                        f.getObservacion()))
                .toList();

        BaseAsistenciaResult base = baseResolver.resolver(empleadoId);
        AsistenciaResumenCalculator.Resumen calc =
                AsistenciaResumenCalculator.calcular(dias, base.getRemuneracionBase());

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
            int omitidos) {
        if (procesados == 0) {
            return "No se importo ningun empleado con la estrategia seleccionada.";
        }
        if (estrategia == AsistenciaImportEstrategia.REEMPLAZAR_PERIODO_COMPLETO) {
            return "La asistencia del periodo fue reemplazada para todos los empleados validos del archivo.";
        }
        if (estrategia == AsistenciaImportEstrategia.REEMPLAZAR_EMPLEADOS_ARCHIVO) {
            return "La asistencia fue reemplazada para los empleados incluidos en el archivo.";
        }
        if (omitidos > 0) {
            return "La asistencia fue importada parcialmente; algunos empleados con asistencia previa fueron omitidos.";
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
        return dto;
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
