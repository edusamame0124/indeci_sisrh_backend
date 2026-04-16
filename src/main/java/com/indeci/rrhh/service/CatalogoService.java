package com.indeci.rrhh.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.rrhh.dto.UbigeoDto;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.BankAccountType;
import com.indeci.rrhh.repository.BankAccountTypeRepository;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.DistrictRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final DistrictRepository districtRepository;
    private final BankRepository bankRepository;
    private final BankAccountTypeRepository tipoCuentaRepository;

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
}