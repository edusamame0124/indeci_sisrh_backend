package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.UbigeoDto;
import com.indeci.rrhh.entity.AirhspVigencia;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.BankAccountType;
import com.indeci.rrhh.entity.CondicionLaboral;
import com.indeci.rrhh.entity.Dependencia;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.EstructuraOrganica;
import com.indeci.rrhh.entity.Nivel;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.entity.Sexo;
import com.indeci.rrhh.entity.TipoComisionAfp;
import com.indeci.rrhh.entity.TipoContrato;
import com.indeci.rrhh.entity.TipoDocumento;
import com.indeci.rrhh.entity.TipoPersonal;
import com.indeci.rrhh.service.CatalogoService;
import com.indeci.rrhh.entity.Profesion;
import com.indeci.rrhh.entity.GradoAcademico;
import com.indeci.rrhh.entity.Sede;
import com.indeci.rrhh.entity.Oficina;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    // 🔹 UBIGEO
    @GetMapping("/ubigeo")
    public ApiResponse<List<UbigeoDto>> ubigeo() {
        return new ApiResponse<>("OK", "Ubigeo completo",
                catalogoService.listarUbigeo());
    }

    // 🔹 BANCOS
    @GetMapping("/bancos")
    public ApiResponse<List<Bank>> bancos() {
        return new ApiResponse<>("OK", "Lista de bancos",
                catalogoService.listarBancos());
    }

    // 🔹 TIPOS DE CUENTA
    @GetMapping("/tipos-cuenta")
    public ApiResponse<List<BankAccountType>> tiposCuenta() {
        return new ApiResponse<>("OK", "Tipos de cuenta",
                catalogoService.listarTiposCuenta());
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
    public ApiResponse<List<TipoPersonal>> tiposPersonal() {

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
    
}
