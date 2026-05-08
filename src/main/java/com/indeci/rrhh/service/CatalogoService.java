package com.indeci.rrhh.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.rrhh.dto.UbigeoDto;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.BankAccountType;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.Sexo;
import com.indeci.rrhh.entity.TipoDocumento;
import com.indeci.rrhh.repository.BankAccountTypeRepository;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.DistrictRepository;
import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.SexoRepository;
import com.indeci.rrhh.repository.TipoDocumentoRepository;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final DistrictRepository districtRepository;
    private final BankRepository bankRepository;
    private final BankAccountTypeRepository tipoCuentaRepository;
    private final SexoRepository sexoRepository;

    private final EstadoCivilRepository estadoCivilRepository;

    private final TipoDocumentoRepository tipoDocumentoRepository;
 

    private final TipoPersonalRepository tipoPersonalRepository;

    private final EstructuraOrganicaRepository estructuraOrganicaRepository;

    private final NivelRepository nivelRepository;

    private final DependenciaRepository dependenciaRepository;

    private final TipoComisionAfpRepository tipoComisionAfpRepository;

    private final AirhspVigenciaRepository airhspVigenciaRepository;

    private final RegimenLaboralRepository regimenLaboralRepository;

    private final TipoContratoRepository tipoContratoRepository;

    private final CondicionLaboralRepository condicionLaboralRepository;
    
    private final RegimenPensionarioRepository regimenPensionarioRepository;
    
    private final ProfesionRepository profesionRepository;

    private final GradoAcademicoRepository gradoAcademicoRepository;
    private final SedeRepository sedeRepository;

    private final OficinaRepository oficinaRepository;

    // 🔹 UBIGEO PRO
    public List<UbigeoDto> listarUbigeo() {
        return districtRepository.listarUbigeoCompleto();
    }

    // 🔹 BANCOS
    public List<Bank> listarBancos() {
        return bankRepository.findAll();
    }

    // 🔹 TIPOS DE CUENTA
    public List<BankAccountType> listarTiposCuenta() {
        return tipoCuentaRepository.findAll();
    }
    
    public List<Sexo> listarSexos() {
        return sexoRepository.findAll();
    }
    public List<EstadoCivil> listarEstadosCiviles() {
        return estadoCivilRepository.findAll();
    }
    public List<TipoDocumento> listarTiposDocumento() {
        return tipoDocumentoRepository.findAll();
    }
    public List<TipoPersonal> listarTiposPersonal() {
        return tipoPersonalRepository.findAll();
    }
    public List<EstructuraOrganica> listarEstructurasOrganicas() {
        return estructuraOrganicaRepository.findAll();
    }
    
    public List<Nivel> listarNiveles() {
        return nivelRepository.findAll();
    }
    
    public List<Dependencia> listarDependencias() {
        return dependenciaRepository.findAll();
    }
    public List<TipoComisionAfp> listarTiposComisionAfp() {
        return tipoComisionAfpRepository.findAll();
    }
    
    public List<AirhspVigencia> listarAirhspVigencias() {
        return airhspVigenciaRepository.findAll();
    }
    
    public List<RegimenLaboral> listarRegimenesLaborales() {
        return regimenLaboralRepository.findAll();
    }
    
    public List<TipoContrato> listarTiposContrato() {
        return tipoContratoRepository.findAll();
    }
    
    public List<CondicionLaboral> listarCondicionesLaborales() {
        return condicionLaboralRepository.findAll();
    }
    
    public List<RegimenPensionario>
    listarRegimenesPensionarios() {

        return regimenPensionarioRepository.findAll();
    }
    public List<Profesion>
    listarProfesiones() {

        return profesionRepository.findAll();
    }

    public List<GradoAcademico>
    listarGradosAcademicos() {

        return gradoAcademicoRepository.findAll();
    }
    public List<Sede> listarSedes() {

        return sedeRepository.findAll();
    }

    public List<Oficina>
    listarOficinas() {

        return oficinaRepository.findAll();
    }

    public List<Oficina>
    listarOficinasPorSede(Long sedeId) {

        return oficinaRepository
                .findBySedeId(sedeId);
    }
    
}