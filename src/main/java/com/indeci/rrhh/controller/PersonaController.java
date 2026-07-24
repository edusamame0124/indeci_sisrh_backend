package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.security.auth.SisrhSecurityExpressions;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.rrhh.dto.MiPerfilUpdateDto;
import com.indeci.rrhh.dto.PersonaEmpleadoDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.dto.PersonaResumenPageDto;
import com.indeci.rrhh.service.PersonaService;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/rrhh")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ + " or " + SisrhSecurityExpressions.ADM_USERS)
public class PersonaController {

    private final PersonaService personaService;
    
    
    @GetMapping(
            value="/persona/{id}/foto",
            produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> foto(
            @PathVariable Long id) {

        byte[] archivo =
                personaService.obtenerFoto(id);

        return ResponseEntity
                .ok()
                .body(archivo);
    }
    
    @PostMapping(
            value="/persona/{id}/foto",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE + " or " + SisrhSecurityExpressions.ADM_USERS)
    public ApiResponse<Void> subirFoto(

            @PathVariable Long id,

            @RequestPart("file")
            MultipartFile file) {

        personaService.actualizarFoto(
                id,
                file);

        return new ApiResponse<>(
                "OK",
                "Foto actualizada",
                null);
    }

    // CREAR
    @PostMapping("/persona")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE + " or " + SisrhSecurityExpressions.ADM_USERS)
    public ApiResponse<Void> guardar(@RequestBody PersonaEmpleadoDto dto) {
        personaService.guardar(dto);
        return new ApiResponse<>("OK", "Registrado correctamente", null);
    }

    // LISTAR completo — 1 query JOIN (usado por hub, picker y otros componentes)
    @GetMapping("/persona")
    public ApiResponse<List<PersonaResumenDto>> listar() {
        return new ApiResponse<>("OK", "Listado correcto", personaService.listar());
    }

    // LISTAR PAGINADO — para la pantalla principal de personas
    @GetMapping("/persona/page")
    public ApiResponse<PersonaResumenPageDto> listarPaginado(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
        return new ApiResponse<>("OK", "Listado paginado", personaService.listarPaginado(q, page, size));
    }
    /**
     * Autoservicio: el empleado resuelve SU PROPIO perfil vía JWT (SecurityUtil),
     * no recibe un id del cliente. Override necesario: EMP_READ (clase) es para
     * personal RRHH que consulta a OTROS empleados; el rol EMPLEADO no lo tiene
     * y no debe tenerlo (le daría acceso a /persona/{id} de cualquier persona).
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/persona/me")
    public ApiResponse<PersonaEmpleadoResponseDto> obtenerMiPerfil() {
        return new ApiResponse<>(
                "OK",
                "Detalle del empleado logueado",
                personaService.obtenerMiPerfil());
    }

    /** Autoservicio — solo campos de contacto (ver MiPerfilUpdateDto). */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/persona/me")
    public ApiResponse<PersonaEmpleadoResponseDto> actualizarMiPerfil(
            @RequestBody MiPerfilUpdateDto dto) {
        return new ApiResponse<>(
                "OK",
                "Perfil actualizado",
                personaService.actualizarMiPerfil(dto));
    }

    /** Autoservicio — foto del empleado autenticado. 204 si aún no subió foto (no es un error). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/persona/me/foto", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> fotoMiPerfil() {
        byte[] foto = personaService.obtenerFotoMiPerfil();

        if (foto == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok().body(foto);
    }

    /** Autoservicio — subir/actualizar foto del empleado autenticado. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/persona/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> subirFotoMiPerfil(@RequestPart("file") MultipartFile file) {
        personaService.actualizarFotoMiPerfil(file);
        return new ApiResponse<>("OK", "Foto actualizada", null);
    }
    // DETALLE
    @GetMapping("/persona/{id}")
    public ApiResponse<PersonaEmpleadoResponseDto> obtener(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Detalle correcto", personaService.obtenerPorId(id));
    }

    // ACTUALIZAR
    @PutMapping("/persona/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE + " or " + SisrhSecurityExpressions.ADM_USERS)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody PersonaEmpleadoDto dto
    ) {
        personaService.actualizar(id, dto);
        return new ApiResponse<>("OK", "Actualizado correctamente", null);
    }

    // ELIMINAR
    @DeleteMapping("/persona/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE + " or " + SisrhSecurityExpressions.ADM_USERS)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        personaService.eliminar(id);
        return new ApiResponse<>("OK", "Eliminado correctamente", null);
    }
    
}