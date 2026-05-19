package com.indeci.rrhh.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.CatalogoNombreRequest;
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

    private static final int VIGENTE = 1;
    private static final int BAJA = 0;

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
    private final TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;

private final EstadoSolicitudRepository estadoSolicitudRepository;

    public List<UbigeoDto> listarUbigeo() {
        return districtRepository.listarUbigeoCompleto();
    }

    public List<Bank> listarBancos() {
        return bankRepository.findByActivoOrderByNameAsc(VIGENTE);
    }

    public List<BankAccountType> listarTiposCuenta() {
        return tipoCuentaRepository.findByActivoOrderByNameAsc(VIGENTE);
    }

    @Transactional
    @Auditable(accion = "CATALOGO_BANCO_CREAR")
    public Bank crearBanco(CatalogoNombreRequest req) {
        String nombre = normalizarNombre(req.name());
        validarNombreUnicoBanco(nombre, null);
        Bank b = new Bank();
        b.setName(nombre);
        b.setActivo(VIGENTE);
        return bankRepository.save(b);
    }

    @Transactional
    @Auditable(accion = "CATALOGO_BANCO_EDITAR")
    public Bank actualizarBanco(Long id, CatalogoNombreRequest req) {
        Bank b = obtenerBancoVigente(id);
        String nombre = normalizarNombre(req.name());
        validarNombreUnicoBanco(nombre, id);
        b.setName(nombre);
        return bankRepository.save(b);
    }

    @Transactional
    @Auditable(accion = "CATALOGO_BANCO_BAJA")
    public void eliminarBanco(Long id) {
        Bank b = obtenerBancoVigente(id);
        b.setActivo(BAJA);
        bankRepository.save(b);
    }

    @Transactional
    @Auditable(accion = "CATALOGO_TIPO_CUENTA_CREAR")
    public BankAccountType crearTipoCuenta(CatalogoNombreRequest req) {
        String nombre = normalizarNombre(req.name());
        validarNombreUnicoTipo(nombre, null);
        BankAccountType t = new BankAccountType();
        t.setName(nombre);
        t.setActivo(VIGENTE);
        return tipoCuentaRepository.save(t);
    }

    @Transactional
    @Auditable(accion = "CATALOGO_TIPO_CUENTA_EDITAR")
    public BankAccountType actualizarTipoCuenta(Long id, CatalogoNombreRequest req) {
        BankAccountType t = obtenerTipoVigente(id);
        String nombre = normalizarNombre(req.name());
        validarNombreUnicoTipo(nombre, id);
        t.setName(nombre);
        return tipoCuentaRepository.save(t);
    }

    @Transactional
    @Auditable(accion = "CATALOGO_TIPO_CUENTA_BAJA")
    public void eliminarTipoCuenta(Long id) {
        BankAccountType t = obtenerTipoVigente(id);
        t.setActivo(BAJA);
        tipoCuentaRepository.save(t);
    }

    private Bank obtenerBancoVigente(Long id) {
        return bankRepository.findByIdAndActivo(id, VIGENTE)
                .orElseThrow(() -> new NegocioException("Banco no encontrado"));
    }

    private BankAccountType obtenerTipoVigente(Long id) {
        return tipoCuentaRepository.findByIdAndActivo(id, VIGENTE)
                .orElseThrow(() -> new NegocioException("Tipo de cuenta no encontrado"));
    }

    private void validarNombreUnicoBanco(String nombre, Long excludeId) {
        boolean dup = excludeId == null
                ? bankRepository.existsByNameIgnoreCaseAndActivo(nombre, VIGENTE)
                : bankRepository.existsByNameIgnoreCaseAndActivoAndIdNot(nombre, VIGENTE, excludeId);
        if (dup) {
            throw new NegocioException("Ya existe un banco activo con ese nombre");
        }
    }

    private void validarNombreUnicoTipo(String nombre, Long excludeId) {
        boolean dup = excludeId == null
                ? tipoCuentaRepository.existsByNameIgnoreCaseAndActivo(nombre, VIGENTE)
                : tipoCuentaRepository.existsByNameIgnoreCaseAndActivoAndIdNot(
                        nombre, VIGENTE, excludeId);
        if (dup) {
            throw new NegocioException("Ya existe un tipo de cuenta activo con ese nombre");
        }
    }

    private static String normalizarNombre(String raw) {
        return raw.trim().toUpperCase(Locale.forLanguageTag("es-PE"));
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
    
    public List<TipoSolicitudRrhh>
    listarTiposSolicitudRrhh() {

        return tipoSolicitudRrhhRepository.findAll();
    }

    public List<EstadoSolicitud>
    listarEstadosSolicitud() {

        return estadoSolicitudRepository.findAll();
    }
    
}
