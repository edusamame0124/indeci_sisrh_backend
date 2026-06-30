package com.indeci.rrhh.service.support;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EmpleadoBanco;
import com.indeci.rrhh.entity.EmpleadoPuesto;

import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.NivelRepository;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.ModalidadCasRepository;

@Component
@RequiredArgsConstructor
public class MovimientoPlanillaSnapshotFactory {

    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final EmpleadoBancoRepository empleadoBancoRepository;
    private final EmpleadoPuestoRepository empleadoPuestoRepository;
    
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final NivelRepository nivelRepository;
    private final BankRepository bankRepository;
    private final ModalidadCasRepository modalidadCasRepository;

    /**
     * Aplica el Principio de Responsabilidad Única (SRP) tomando una "foto histórica"
     * de la metadata del empleado en el momento exacto del cálculo de la planilla.
     */
    public void capturarMetadata(MovimientoPlanilla mov) {
        if (mov.getEmpleadoId() == null) return;
        
        EmpleadoPlanilla empPlanilla = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(mov.getEmpleadoId(), 1).orElse(null);
        EmpleadoBanco empBanco = empleadoBancoRepository.findByEmpleadoIdAndActivo(mov.getEmpleadoId(), 1).stream().findFirst().orElse(null);
        EmpleadoPuesto empPuesto = mov.getEmpleadoPuestoId() != null ? 
                                    empleadoPuestoRepository.findById(mov.getEmpleadoPuestoId()).orElse(null) : null;
        
        // 1. Snapshot del Régimen Laboral
        if (empPlanilla != null && empPlanilla.getRegimenLaboralId() != null) {
            regimenLaboralRepository.findById(empPlanilla.getRegimenLaboralId())
                .ifPresent(reg -> mov.setRegimenLaboralSnapshot(reg.getNombre()));
                
            // Snapshot de la Modalidad CAS o SERVIR
            if (empPlanilla.getModalidadCasId() != null) {
                modalidadCasRepository.findById(empPlanilla.getModalidadCasId())
                    .ifPresent(mod -> mov.setModalidadSnapshot(mod.getNombre()));
            }
        }

        // 2. Snapshot de la Cuenta Bancaria
        if (empBanco != null) {
            String cuenta = "";
            if (empBanco.getBankId() != null) {
                cuenta += bankRepository.findById(empBanco.getBankId())
                        .map(b -> b.getName() + " ")
                        .orElse("");
            }
            if (empBanco.getNumeroCuenta() != null) {
                cuenta += empBanco.getNumeroCuenta();
            }
            mov.setCuentaBancariaSnapshot(cuenta.trim());
        }

        // 3. Snapshot del Nivel Remunerativo (Cargo/Escala base)
        if (empPuesto != null && empPuesto.getNivelId() != null) {
            nivelRepository.findById(empPuesto.getNivelId())
                .ifPresent(niv -> mov.setNivelRemunerativoSnapshot(niv.getNombre()));
        }
    }
}
