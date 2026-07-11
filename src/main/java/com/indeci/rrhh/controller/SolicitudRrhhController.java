package com.indeci.rrhh.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.DocumentoAdjuntoDto;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.dto.SolicitudRrhhResponseDto;
import com.indeci.rrhh.service.SolicitudRrhhService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/solicitudes")
@RequiredArgsConstructor
//@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class SolicitudRrhhController {

    private final SolicitudRrhhService service;
    private final com.indeci.rrhh.report.service.PapeletaReportService papeletaReportService;

    /**
     * SPEC_VACACIONES F9.1-bis — descarga el PDF oficial de la papeleta para firmar.
     * Reusa PapeletaReportService (Jasper) que exporta a archivo local y devuelve su ruta.
     */
    @GetMapping("/{id}/papeleta/pdf")
    public org.springframework.http.ResponseEntity<byte[]> descargarPapeleta(
            @PathVariable Long id) throws java.io.IOException {

        String ruta = papeletaReportService.generarPdf(id);
        byte[] pdf = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(ruta));

        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=papeleta_" + id + ".pdf")
                .body(pdf);
    }

    // ==========================================
    // REGISTRAR
    // ==========================================
    @PostMapping(
            value="/registrar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> registrar(

            @RequestPart("datos")
            SolicitudRrhhDto dto,

            @RequestPart(
                    value="sustento",
                    required=false)
            MultipartFile sustento,

            @RequestPart(
                    value="documentos",
                    required=false)
            List<MultipartFile> documentos) {

        Long id = service.registrar(
                dto,
                sustento,
                documentos);

        return new ApiResponse<>(
                "OK",
                "Solicitud registrada",
                id);
    }

    // ==========================================
    // LISTAR EMPLEADO
    // ==========================================


  //  @PreAuthorize("hasAuthority('PAP_EMPLEADO')")
    @GetMapping("/mis-solicitudes")
    public ApiResponse<List<SolicitudRrhhResponseDto>>
    misSolicitudes() {

        return new ApiResponse<>(
                "OK",
                "Solicitudes empleado",
                service.listarMisSolicitudes());
        }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listarEmpleado(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Solicitudes empleado", service.listarPorEmpleado(empleadoId));

    }

    /**
     * Gate de Modalidad de Teletrabajo (Ley N° 31572) — indica si el empleado
     * logueado puede generar reportes de teletrabajo (resolución activa en legajo).
     * Alimenta el bloqueo Poka-Yoke del botón "Reporte Teletrabajo" en el dashboard.
     */
    @GetMapping("/mi-teletrabajo")
    public ApiResponse<Boolean> miTeletrabajo() {
        return new ApiResponse<>("OK", "Habilitación teletrabajo", service.esTeletrabajadorActual());
    }

    @PutMapping("/enviar/{id}")
    //@PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> enviar(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false)
            MultipartFile file,
            @RequestParam(value = "observacion", required = false) String observacion) {

        service.enviar(id, file, observacion);
        return new ApiResponse<>("OK", "Solicitud enviada", null);
    }

    
    @PreAuthorize("hasAuthority('PAP_JEFE')")
    @PutMapping("/aprobar-jefe/{id}")
    //@PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> aprobarJefe(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false)
            MultipartFile file,
            @RequestParam(value = "observacion", required = false) String observacion) {

        service.aprobarSupervisor(id, file, observacion);
        return new ApiResponse<>("OK", "Solicitud aprobada por jefe", null);
    }

    
    @PreAuthorize("hasAuthority('PAP_JEFE')")
    @PutMapping("/rechazar-jefe/{id}")
   // @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> rechazarJefe(@PathVariable Long id) {
        service.rechazarSupervisor(id);
        return new ApiResponse<>("OK", "Solicitud rechazada por jefe", null);
    }

    
    @PreAuthorize("hasAuthority('PAP_APROBAR_RRHH')")
    @PutMapping("/aprobar-rrhh/{id}")
    public ApiResponse<Void>
    aprobarRrhh(

            @PathVariable Long id,

            @RequestParam(value = "file", required = false)
            MultipartFile file,

            @RequestParam(
                    value = "observacion",
                    required = false)
            String observacion) {

        service.aprobarRrhh(
                id,
                file,
                observacion);

        return new ApiResponse<>(
                "OK",
                "Solicitud aprobada por RRHH",
                null);
    }

  
    @PreAuthorize("hasAuthority('PAP_APROBAR_RRHH')")
    @PutMapping("/rechazar-rrhh/{id}")
   // @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> rechazarRrhh(@PathVariable Long id) {
        service.rechazarRrhh(id);
        return new ApiResponse<>("OK", "Solicitud rechazada por RRHH", null);
    }

    @PutMapping("/editar/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> editar(@PathVariable Long id, @RequestBody SolicitudRrhhDto dto) {
        service.editar(id, dto);
        return new ApiResponse<>("OK", "Solicitud editada", null);
    }

    @PutMapping("/anular/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> anular(@PathVariable Long id) {
        service.anular(id);
        return new ApiResponse<>("OK", "Solicitud anulada", null);
    }
    

    @PreAuthorize("hasAuthority('PAP_JEFE')")
    @GetMapping("/mis-colaboradores")
    public ApiResponse<List<SolicitudRrhhResponseDto>>
    misColaboradores() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                service.listarMisColaboradores());
        
    }
        
    @GetMapping("/jefe/{jefeId}")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listarJefe(@PathVariable Long jefeId) {
        return new ApiResponse<>("OK", "Listado correcto", service.listarPorJefe(jefeId));

    }
    
    @PreAuthorize("hasAuthority('PAP_RRHH')")
    @GetMapping("/todas")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listarTodas() {
        return new ApiResponse<>("OK", "Listado correcto", service.listarTodas());
    }
}