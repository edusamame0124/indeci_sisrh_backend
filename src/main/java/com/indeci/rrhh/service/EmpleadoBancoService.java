package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoBancoDto;
import com.indeci.rrhh.dto.EmpleadoBancoResponseDto;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.BankAccountType;
import com.indeci.rrhh.entity.EmpleadoBanco;
import com.indeci.rrhh.repository.BankAccountTypeRepository;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoBancoService {

    private final EmpleadoBancoRepository repository;
    private final AuditoriaContext auditoriaContext;
    private final BankRepository bankRepository;
    private final BankAccountTypeRepository bankAccountTypeRepository;

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_CUENTA_BANCO")
    public void guardar(EmpleadoBancoDto dto) {

        // 🔥 VALIDAR SOLO SI MARCAN COMO PLANILLA
        if (dto.getEsCuentaPlanilla() != null && dto.getEsCuentaPlanilla() == 1) {

            Optional<EmpleadoBanco> existente =
                    repository.findByEmpleadoIdAndEsCuentaPlanillaAndActivo(
                            dto.getEmpleadoId(), 1, 1);

            if (existente.isPresent()) {
                auditoriaContext.setDetalle("Ya existe cuenta planilla para empleado: " + dto.getEmpleadoId());
                throw new NegocioException("Ya existe una cuenta de planilla");
            }
        }

        // 🔹 guardar normal
        EmpleadoBanco entity = new EmpleadoBanco();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setBankId(dto.getBankId());
        entity.setAccountTypeId(dto.getAccountTypeId());
        entity.setNumeroCuenta(dto.getNumeroCuenta());
        entity.setCci(dto.getCci());
        entity.setEsCuentaPlanilla(dto.getEsCuentaPlanilla());
        entity.setActivo(1);
        entity.setFechaInicio(LocalDate.now());
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Cuenta creada correctamente");
    }

    // ============================
    // LISTAR
    // ============================
    public List<EmpleadoBancoResponseDto> listar(Long empleadoId) {

        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .map(e -> {
                    EmpleadoBancoResponseDto dto = new EmpleadoBancoResponseDto();
                    dto.setId(e.getId());

                    dto.setBankId(
                            e.getBankId());

                    dto.setAccountTypeId(
                            e.getAccountTypeId());
                    if (e.getBankId() != null) {

                        Bank bank =
                                bankRepository
                                        .findById(
                                                e.getBankId())
                                        .orElse(null);

                        if (bank != null) {

                            dto.setBank(
                                    bank.getName());
                        }
                    }

                    if (e.getAccountTypeId() != null) {

                        BankAccountType tipoCuenta =
                                bankAccountTypeRepository
                                        .findById(
                                                e.getAccountTypeId())
                                        .orElse(null);

                        if (tipoCuenta != null) {

                            dto.setAccountType(
                                    tipoCuenta.getName());
                        }
                    }

                    dto.setNumeroCuenta(
                            e.getNumeroCuenta());

                    dto.setCci(
                            e.getCci());

                    dto.setEsCuentaPlanilla(
                            e.getEsCuentaPlanilla());

                    dto.setActivo(
                            e.getActivo());
                    return dto;
                }).toList();
    }

    // ============================
    // ACTUALIZAR
    // ============================
    @Auditable(accion = "ACTUALIZAR_CUENTA_BANCO")
    public void actualizar(Long id, EmpleadoBancoDto dto) {

        EmpleadoBanco entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Cuenta no encontrada"));

        // 🔥 VALIDAR SOLO SI CAMBIA A PLANILLA
        if (dto.getEsCuentaPlanilla() != null && dto.getEsCuentaPlanilla() == 1) {

            Optional<EmpleadoBanco> existente =
                    repository.findByEmpleadoIdAndEsCuentaPlanillaAndActivo(
                            entity.getEmpleadoId(), 1, 1);

            // 🔥 VALIDAR QUE NO SEA LA MISMA CUENTA
            if (existente.isPresent() && !existente.get().getId().equals(id)) {
                auditoriaContext.setDetalle("Intento de duplicar cuenta planilla");
                throw new NegocioException("Ya existe una cuenta de planilla");
            }
        }

        entity.setBankId(dto.getBankId());
        entity.setAccountTypeId(dto.getAccountTypeId());
        entity.setNumeroCuenta(dto.getNumeroCuenta());
        entity.setCci(dto.getCci());
        entity.setEsCuentaPlanilla(dto.getEsCuentaPlanilla());

        repository.save(entity);

        auditoriaContext.setDetalle("Cuenta actualizada ID: " + id);
    }

    // ============================
    // ELIMINAR (LÓGICO)
    // ============================
    @Auditable(accion = "ELIMINAR_CUENTA_BANCO")
    public void eliminar(Long id) {

        EmpleadoBanco entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Cuenta no encontrada"));

        entity.setActivo(0);
        entity.setFechaFin(LocalDate.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Cuenta bancaria desactivada ID: " + id);
    }
}