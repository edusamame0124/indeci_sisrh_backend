package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.dto.EmpleadoFlowStatusDto;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;

/**
 * Spec 012 / C3 (BKD-006) — Tests del estado agregado del flujo de empleado.
 *   - empleado con todos los pasos → todo true
 *   - empleado sin ningún registro → todo false
 *   - empleado parcial (solo puesto y banco) → mezcla correcta de banderas
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoFlowStatusServiceTest {

    @Mock private EmpleadoPuestoRepository puestoRepository;
    @Mock private EmpleadoBancoRepository bancoRepository;
    @Mock private EmpleadoPensionRepository pensionRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private EmpleadoConceptoRepository conceptoRepository;

    @InjectMocks private EmpleadoFlowStatusService service;

    private static final Long EMP = 42L;

    @Test
    void empleadoConTodosLosPasosDevuelveTodoTrue() {
        when(puestoRepository.existsByEmpleadoId(EMP)).thenReturn(true);
        when(bancoRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(true);
        when(pensionRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(true);
        when(planillaRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(true);
        when(conceptoRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(true);

        EmpleadoFlowStatusDto dto = service.obtener(EMP);

        assertThat(dto.getEmpleadoId()).isEqualTo(EMP);
        assertThat(dto.isPuesto()).isTrue();
        assertThat(dto.isBanco()).isTrue();
        assertThat(dto.isPension()).isTrue();
        assertThat(dto.isPlanilla()).isTrue();
        assertThat(dto.isConceptos()).isTrue();
    }

    @Test
    void empleadoSinRegistrosDevuelveTodoFalse() {
        when(puestoRepository.existsByEmpleadoId(EMP)).thenReturn(false);
        when(bancoRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(false);
        when(pensionRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(false);
        when(planillaRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(false);
        when(conceptoRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(false);

        EmpleadoFlowStatusDto dto = service.obtener(EMP);

        assertThat(dto.isPuesto()).isFalse();
        assertThat(dto.isBanco()).isFalse();
        assertThat(dto.isPension()).isFalse();
        assertThat(dto.isPlanilla()).isFalse();
        assertThat(dto.isConceptos()).isFalse();
    }

    @Test
    void empleadoParcialMarcaSoloLosPasosConRegistros() {
        when(puestoRepository.existsByEmpleadoId(EMP)).thenReturn(true);
        when(bancoRepository.existsByEmpleadoIdAndActivo(EMP, 1)).thenReturn(true);
        // pensión, planilla y conceptos quedan en false (valor por defecto del mock).

        EmpleadoFlowStatusDto dto = service.obtener(EMP);

        assertThat(dto.isPuesto()).isTrue();
        assertThat(dto.isBanco()).isTrue();
        assertThat(dto.isPension()).isFalse();
        assertThat(dto.isPlanilla()).isFalse();
        assertThat(dto.isConceptos()).isFalse();
    }
}
