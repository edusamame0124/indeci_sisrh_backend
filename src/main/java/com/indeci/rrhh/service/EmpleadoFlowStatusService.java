package com.indeci.rrhh.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.dto.EmpleadoFlowStatusDto;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 012 / C3 (BKD-006) — Estado agregado del flujo de configuración de un
 * empleado.
 *
 * <p>Resuelve en una sola consulta lo que el frontend obtenía con 5 GET
 * paralelos (puesto, banco, pensión, planilla, conceptos). Cada paso refleja
 * el mismo criterio de los endpoints {@code listar} de cada recurso: el puesto
 * cuenta cualquier registro; los demás, solo registros activos.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoFlowStatusService {

    private static final Integer ACTIVO = 1;

    private final EmpleadoPuestoRepository puestoRepository;
    private final EmpleadoBancoRepository bancoRepository;
    private final EmpleadoPensionRepository pensionRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final EmpleadoConceptoRepository conceptoRepository;

    @Transactional(readOnly = true)
    public EmpleadoFlowStatusDto obtener(Long empleadoId) {
        EmpleadoFlowStatusDto dto = new EmpleadoFlowStatusDto();
        dto.setEmpleadoId(empleadoId);
        dto.setPuesto(puestoRepository.existsByEmpleadoId(empleadoId));
        dto.setBanco(bancoRepository.existsByEmpleadoIdAndActivo(empleadoId, ACTIVO));
        dto.setPension(pensionRepository.existsByEmpleadoIdAndActivo(empleadoId, ACTIVO));
        dto.setPlanilla(planillaRepository.existsByEmpleadoIdAndActivo(empleadoId, ACTIVO));
        dto.setConceptos(conceptoRepository.existsByEmpleadoIdAndActivo(empleadoId, ACTIVO));
        return dto;
    }
}
