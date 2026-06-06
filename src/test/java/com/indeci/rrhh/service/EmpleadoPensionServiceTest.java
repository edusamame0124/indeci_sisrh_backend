package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.dto.TasasVigentesPensionDto;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.RegimenPensionarioRepository;
import com.indeci.rrhh.repository.TipoComisionAfpRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmpleadoPensionServiceTest {

    @Mock private EmpleadoPensionRepository repository;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private RegimenPensionarioRepository regimenPensionarioRepository;
    @Mock private TipoComisionAfpRepository tipoComisionAfpRepository;
    @Mock private ParametroRemunerativoService parametroService;

    private EmpleadoPensionService service;

    @BeforeEach
    void setUp() {
        service = new EmpleadoPensionService(
                repository,
                auditoriaContext,
                regimenPensionarioRepository,
                tipoComisionAfpRepository,
                parametroService);
    }

    @Test
    void tasasVigentes_para_sin_regimen_no_retorna_tasas_ni_consulta_parametros() {
        when(regimenPensionarioRepository.findById(14L))
                .thenReturn(Optional.of(regimen("SIN_REGIMEN")));

        TasasVigentesPensionDto dto = service.tasasVigentes(14L, null, 2026);

        assertThat(dto.getTipoRegimen()).isEqualTo("SIN_REGIMEN");
        assertThat(dto.getAporte()).isNull();
        assertThat(dto.getComision()).isNull();
        assertThat(dto.getPrima()).isNull();
        assertThat(dto.isComisionParametrizada()).isTrue();
        verifyNoInteractions(parametroService);
    }

    @Test
    void tasasVigentes_para_pensionista_no_retorna_tasas_ni_consulta_parametros() {
        when(regimenPensionarioRepository.findById(12L))
                .thenReturn(Optional.of(regimen("PENSIONISTA")));

        TasasVigentesPensionDto dto = service.tasasVigentes(12L, null, 2026);

        assertThat(dto.getTipoRegimen()).isEqualTo("PENSIONISTA");
        assertThat(dto.getAporte()).isNull();
        assertThat(dto.getComision()).isNull();
        assertThat(dto.getPrima()).isNull();
        assertThat(dto.isComisionParametrizada()).isTrue();
        verifyNoInteractions(parametroService);
    }

    private RegimenPensionario regimen(String tipo) {
        RegimenPensionario regimen = new RegimenPensionario();
        regimen.setId(1L);
        regimen.setTipo(tipo);
        regimen.setCodigo(tipo);
        regimen.setNombre(tipo);
        regimen.setActivo(1);
        return regimen;
    }
}
