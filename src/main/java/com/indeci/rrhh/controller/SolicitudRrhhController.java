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

    // ==========================================
    // REGISTRAR
    // ==========================================
    @PostMapping(
            value="/registrar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> registrar(

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

        service.registrar(
                dto,
                sustento,
                documentos);

        return new ApiResponse<>(
                "OK",
                "Solicitud registrada",
                null);
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

    @PutMapping("/enviar/{id}")
    //@PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> enviar(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "observacion", required = false) String observacion) {

        service.enviar(id, file, observacion);
        return new ApiResponse<>("OK", "Solicitud enviada", null);
    }

    
    //@PreAuthorize("hasAuthority('PAP_JEFE')")
    @PutMapping("/aprobar-jefe/{id}")
    //@PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> aprobarJefe(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "observacion", required = false) String observacion) {

        service.aprobarSupervisor(id, file, observacion);
        return new ApiResponse<>("OK", "Solicitud aprobada por jefe", null);
    }

    
    //@PreAuthorize("hasAuthority('PAP_JEFE')")
    @PutMapping("/rechazar-jefe/{id}")
   // @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> rechazarJefe(@PathVariable Long id) {
        service.rechazarSupervisor(id);
        return new ApiResponse<>("OK", "Solicitud rechazada por jefe", null);
    }

    
    //@PreAuthorize("hasAuthority('PAP_RRHH')")
    @PutMapping("/aprobar-rrhh/{id}")
    public ApiResponse<Void>
    aprobarRrhh(

            @PathVariable Long id,

            @RequestParam("file")
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

  
    //@PreAuthorize("hasAuthority('PAP_RRHH')")
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
    

  //  @PreAuthorize("hasAuthority('PAP_JEFE')")
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
    
  //  @PreAuthorize("hasAuthority('PAP_RRHH')")
    @GetMapping("/todas")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listarTodas() {
        return new ApiResponse<>("OK", "Listado correcto", service.listarTodas());
    }
}