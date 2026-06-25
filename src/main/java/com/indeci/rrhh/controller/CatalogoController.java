package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.security.auth.SisrhSecurityExpressions;
import com.indeci.rrhh.dto.CatalogoNombreRequest;
import com.indeci.rrhh.dto.DocumentoRequeridoDto;
import com.indeci.rrhh.dto.UbigeoDto;
import com.indeci.rrhh.entity.AirhspVigencia;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.BankAccountType;
import com.indeci.rrhh.entity.CondicionLaboral;
import com.indeci.rrhh.entity.Dependencia;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.EstadoSolicitud;
import com.indeci.rrhh.entity.EstructuraOrganica;
import com.indeci.rrhh.entity.Nivel;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.entity.Sexo;
import com.indeci.rrhh.entity.TipoComisionAfp;
import com.indeci.rrhh.entity.TipoContrato;
import com.indeci.rrhh.entity.TipoDescansoMedico;
import com.indeci.rrhh.entity.TipoDocumento;
import com.indeci.rrhh.entity.TipoLicencia;
import com.indeci.rrhh.entity.TipoPersona;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.entity.TipoVacacion;
import com.indeci.rrhh.entity.TtConformidad;
import com.indeci.rrhh.entity.TtEstadoCumplimiento;
import com.indeci.rrhh.entity.TtModalidad;
import com.indeci.rrhh.repository.TipoLicenciaRepository;
import com.indeci.rrhh.service.CatalogoService;
import com.indeci.rrhh.entity.Profesion;
import com.indeci.rrhh.entity.GradoAcademico;
import com.indeci.rrhh.entity.Sede;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Cargo;
import com.indeci.rrhh.entity.NivelInstruccion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('CAT_WRITE')")
public class CatalogoController {

  

    private final CatalogoService catalogoService;

 

    @GetMapping("/ubigeo")
    public ApiResponse<List<UbigeoDto>> ubigeo() {
        return new ApiResponse<>("OK", "Ubigeo completo",
                catalogoService.listarUbigeo());
    }

    @GetMapping("/bancos")
    public ApiResponse<List<Bank>> bancos() {
        return new ApiResponse<>("OK", "Lista de bancos",
                catalogoService.listarBancos());
    }

    @GetMapping("/tipos-cuenta")
    public ApiResponse<List<BankAccountType>> tiposCuenta() {
        return new ApiResponse<>("OK", "Tipos de cuenta",
                catalogoService.listarTiposCuenta());
    }

    @PostMapping("/bancos")
    @PreAuthorize(SisrhSecurityExpressions.CAT_WRITE)
    public ApiResponse<Bank> crearBanco(@Valid @RequestBody CatalogoNombreRequest body) {
        Bank saved = catalogoService.crearBanco(body);
        return new ApiResponse<>("OK", "Banco registrado", saved);
    }

    @PutMapping("/bancos/{id}")
    @PreAuthorize(SisrhSecurityExpressions.CAT_WRITE)
    public ApiResponse<Bank> actualizarBanco(
            @PathVariable Long id,
            @Valid @RequestBody CatalogoNombreRequest body) {
        Bank saved = catalogoService.actualizarBanco(id, body);
        return new ApiResponse<>("OK", "Banco actualizado", saved);
    }

    @DeleteMapping("/bancos/{id}")
    @PreAuthorize(SisrhSecurityExpressions.CAT_WRITE)
    public ApiResponse<Void> eliminarBanco(@PathVariable Long id) {
        catalogoService.eliminarBanco(id);
        return new ApiResponse<>("OK", "Banco dado de baja", null);
    }

    @PostMapping("/tipos-cuenta")
    @PreAuthorize(SisrhSecurityExpressions.CAT_WRITE)
    public ApiResponse<BankAccountType> crearTipoCuenta(@Valid @RequestBody CatalogoNombreRequest body) {
        BankAccountType saved = catalogoService.crearTipoCuenta(body);
        return new ApiResponse<>("OK", "Tipo de cuenta registrado", saved);
    }

    @PutMapping("/tipos-cuenta/{id}")
    @PreAuthorize(SisrhSecurityExpressions.CAT_WRITE)
    public ApiResponse<BankAccountType> actualizarTipoCuenta(
            @PathVariable Long id,
            @Valid @RequestBody CatalogoNombreRequest body) {
        BankAccountType saved = catalogoService.actualizarTipoCuenta(id, body);
        return new ApiResponse<>("OK", "Tipo de cuenta actualizado", saved);
    }

    @DeleteMapping("/tipos-cuenta/{id}")
    @PreAuthorize(SisrhSecurityExpressions.CAT_WRITE)
    public ApiResponse<Void> eliminarTipoCuenta(@PathVariable Long id) {
        catalogoService.eliminarTipoCuenta(id);
        return new ApiResponse<>("OK", "Tipo de cuenta dado de baja", null);
    }
    @GetMapping("/sexos")
    public ApiResponse<List<Sexo>> sexos() {

        return new ApiResponse<>(
                "OK",
                "Lista sexos",
                catalogoService.listarSexos());
    }
    
    @GetMapping("/estados-civiles")
    public ApiResponse<List<EstadoCivil>> estadosCiviles() {

        return new ApiResponse<>(
                "OK",
                "Lista estados civiles",
                catalogoService.listarEstadosCiviles());
    }
    @GetMapping("/tipos-documento")
    public ApiResponse<List<TipoDocumento>> tiposDocumento() {

        return new ApiResponse<>(
                "OK",
                "Lista tipos documento",
                catalogoService.listarTiposDocumento());
    }
    
    @GetMapping("/tipos-personal")
    public ApiResponse<List<TipoPersona>> tiposPersonal() {

        return new ApiResponse<>(
                "OK",
                "Lista tipos personal",
                catalogoService.listarTiposPersonal());
    }
    
    @GetMapping("/estructuras-organicas")
    public ApiResponse<List<EstructuraOrganica>> estructurasOrganicas() {

        return new ApiResponse<>(
                "OK",
                "Lista estructuras organicas",
                catalogoService.listarEstructurasOrganicas());
    }
    
    @GetMapping("/niveles")
    public ApiResponse<List<Nivel>> niveles() {

        return new ApiResponse<>(
                "OK",
                "Lista niveles",
                catalogoService.listarNiveles());
    }
    @GetMapping("/dependencias")
    public ApiResponse<List<Dependencia>> dependencias() {

        return new ApiResponse<>(
                "OK",
                "Lista dependencias",
                catalogoService.listarDependencias());
    }

    @GetMapping("/cargos")
    public ApiResponse<List<Cargo>> cargos() {
        return new ApiResponse<>(
                "OK",
                "Lista cargos",
                catalogoService.listarCargos());
    }

    @GetMapping("/tipos-comision-afp")
    public ApiResponse<List<TipoComisionAfp>> tiposComisionAfp() {

        return new ApiResponse<>(
                "OK",
                "Lista tipos comision AFP",
                catalogoService.listarTiposComisionAfp());
    }
    
    @GetMapping("/airhsp-vigencias")
    public ApiResponse<List<AirhspVigencia>> airhspVigencias() {

        return new ApiResponse<>(
                "OK",
                "Lista vigencias AIRHSP",
                catalogoService.listarAirhspVigencias());
    }
    @GetMapping("/regimenes-laborales")
    public ApiResponse<List<RegimenLaboral>> regimenesLaborales() {

        return new ApiResponse<>(
                "OK",
                "Lista regimenes laborales",
                catalogoService.listarRegimenesLaborales());
    }
    
    @GetMapping("/tipos-contrato")
    public ApiResponse<List<TipoContrato>> tiposContrato() {

        return new ApiResponse<>(
                "OK",
                "Lista tipos contrato",
                catalogoService.listarTiposContrato());
    }
    
    @GetMapping("/condiciones-laborales")
    public ApiResponse<List<CondicionLaboral>> condicionesLaborales() {

        return new ApiResponse<>(
                "OK",
                "Lista condiciones laborales",
                catalogoService.listarCondicionesLaborales());
    }
    
    @GetMapping("/regimenes-pensionarios")
    public ApiResponse<List<RegimenPensionario>>
    regimenesPensionarios() {

        return new ApiResponse<>(
                "OK",
                "Lista regimenes pensionarios",
                catalogoService
                        .listarRegimenesPensionarios());
    }
    @GetMapping("/profesiones")
    public ApiResponse<List<Profesion>>
    profesiones() {

        return new ApiResponse<>(
                "OK",
                "Lista profesiones",
                catalogoService
                        .listarProfesiones());
    }

    @GetMapping("/grados-academicos")
    public ApiResponse<List<GradoAcademico>>
    gradosAcademicos() {

        return new ApiResponse<>(
                "OK",
                "Lista grados academicos",
                catalogoService
                        .listarGradosAcademicos());
    }
    
    @GetMapping("/sedes")
    public ApiResponse<List<Sede>>
    sedes() {

        return new ApiResponse<>(
                "OK",
                "Lista sedes",
                catalogoService.listarSedes());
    }

    @GetMapping("/oficinas")
    public ApiResponse<List<Oficina>>
    oficinas() {

        return new ApiResponse<>(
                "OK",
                "Lista oficinas",
                catalogoService.listarOficinas());
    }

    @GetMapping("/oficinas/sede/{sedeId}")
    public ApiResponse<List<Oficina>>
    oficinasPorSede(
            @PathVariable Long sedeId) {

        return new ApiResponse<>(
                "OK",
                "Oficinas por sede",
                catalogoService
                        .listarOficinasPorSede(
                                sedeId));
    }
    
    @GetMapping("/tipos-solicitud-rrhh")
    public ApiResponse<List<TipoSolicitudRrhh>>
    tiposSolicitudRrhh() {

        return new ApiResponse<>(
                "OK",
                "Lista tipos solicitud RRHH",
                catalogoService
                        .listarTiposSolicitudRrhh());
    }
    @GetMapping("/estados-solicitud")
    public ApiResponse<List<EstadoSolicitud>>
    estadosSolicitud() {

        return new ApiResponse<>(
                "OK",
                "Lista estados solicitud",
                catalogoService
                        .listarEstadosSolicitud());
    }
    
    @GetMapping("/tipos-descanso-medico")
    public ApiResponse<List<TipoDescansoMedico>>
    listar() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                catalogoService.listarActivos());
    }
    
    @GetMapping(
            "/tipo-descanso/{id}/documentos")
    public ApiResponse<List<DocumentoRequeridoDto>>
    documentos(
            @PathVariable Long id) {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                catalogoService.obtenerDocumentos(id));
    }
    
    @GetMapping("/tipos-licencia")
    public ApiResponse<List<TipoLicencia>>
    listarTiposLicencia() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                catalogoService
                        .listarTipoLicencia());
    }

    @GetMapping("/tipos-vacacion")
    public ApiResponse<List<TipoVacacion>>
    listarTiposVacacion() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                catalogoService.listarTiposVacacion());
    }
    
    @GetMapping(
            "/teletrabajo/modalidades")
    public ApiResponse<
            List<TtModalidad>>
    listarModalidades() {

        return new ApiResponse<>(
                "OK",
                "Modalidades teletrabajo",
                catalogoService
                        .listarModalidadesTeletrabajo());
    }
    
    @GetMapping(
            "/teletrabajo/estados-cumplimiento")
    public ApiResponse<
            List<TtEstadoCumplimiento>>
    listarEstadosCumplimiento() {

        return new ApiResponse<>(
                "OK",
                "Estados cumplimiento",
                catalogoService
                        .listarEstadosCumplimiento());
    }
    
    @GetMapping(
            "/teletrabajo/conformidades")
    public ApiResponse<
            List<TtConformidad>>
    listarConformidades() {

        return new ApiResponse<>(
                "OK",
                "Conformidades",
                catalogoService
                        .listarConformidades());
    }
    
    @GetMapping("/niveles-instruccion")
    public ApiResponse<List<NivelInstruccion>>
    listarNivelesInstruccion() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                catalogoService
                        .listarNivelesInstruccion());
    }
    
    
}
