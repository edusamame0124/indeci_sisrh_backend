package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseAsistenciaResolverTest {

    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;

    private BaseAsistenciaResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BaseAsistenciaResolver(
                empleadoPlanillaRepository,
                regimenLaboralRepository,
                List.of(
                        new BaseAsistenciaResolverCas(),
                        new BaseAsistenciaResolver276(),
                        new BaseAsistenciaResolver728Servir(),
                        new BaseAsistenciaResolverFallback()));
    }

    @Test
    void resolver_sinPlanilla_devuelveBaseVaciaConAdvertencia() {
        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(10L, 1))
                .thenReturn(Optional.empty());

        BaseAsistenciaResult result = resolver.resolver(10L);

        assertThat(result.getRemuneracionBase()).isZero();
        assertThat(result.getAdvertencias()).isNotEmpty();
    }

    @Test
    void resolver_cas_sumaAsignacionesFijas() {
        EmpleadoPlanilla planilla = new EmpleadoPlanilla();
        planilla.setSueldoBasico(2500.0);
        planilla.setMovilidad(150.0);
        planilla.setAlimentacion(200.0);
        planilla.setRegimenLaboralId(5L);

        RegimenLaboral regimen = new RegimenLaboral();
        regimen.setCodigo("CAS_1057");

        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(20L, 1))
                .thenReturn(Optional.of(planilla));
        when(regimenLaboralRepository.findById(5L)).thenReturn(Optional.of(regimen));

        BaseAsistenciaResult result = resolver.resolver(20L);

        assertThat(result.getRemuneracionBase()).isEqualTo(2850.0);
        assertThat(result.getOrigen()).contains("CAS");
    }

    @Test
    void resolver_regimen276_usaFallbackConAdvertencia() {
        EmpleadoPlanilla planilla = new EmpleadoPlanilla();
        planilla.setSueldoBasico(3000.0);
        planilla.setRegimenLaboralId(7L);

        RegimenLaboral regimen = new RegimenLaboral();
        regimen.setCodigo("DL_276");

        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(30L, 1))
                .thenReturn(Optional.of(planilla));
        when(regimenLaboralRepository.findById(7L)).thenReturn(Optional.of(regimen));

        BaseAsistenciaResult result = resolver.resolver(30L);

        assertThat(result.getRemuneracionBase()).isEqualTo(3000.0);
        assertThat(result.getOrigen()).contains("276");
        assertThat(result.getAdvertencias()).anyMatch(a -> a.contains("provisional"));
    }

    @Test
    void resolver_regimenDesconocido_usaFallbackSueldoBasico() {
        EmpleadoPlanilla planilla = new EmpleadoPlanilla();
        planilla.setSueldoBasico(1800.0);
        planilla.setRegimenLaboralId(99L);

        RegimenLaboral regimen = new RegimenLaboral();
        regimen.setCodigo("REGIMEN_INTERNO");

        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(40L, 1))
                .thenReturn(Optional.of(planilla));
        when(regimenLaboralRepository.findById(99L)).thenReturn(Optional.of(regimen));

        BaseAsistenciaResult result = resolver.resolver(40L);

        assertThat(result.getRemuneracionBase()).isEqualTo(1800.0);
        assertThat(result.getOrigen()).isEqualTo("FALLBACK_SUELDO_BASICO");
        assertThat(result.getAdvertencias()).anyMatch(a -> a.contains("provisional"));
    }
}
