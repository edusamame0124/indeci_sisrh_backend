package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SuspensionDto;
import com.indeci.rrhh.dto.SuspensionResponseDto;
import com.indeci.rrhh.entity.CatSuspensionSunat;
import com.indeci.rrhh.entity.Suspension;
import com.indeci.rrhh.repository.CatSuspensionSunatRepository;
import com.indeci.rrhh.repository.SuspensionRepository;

/**
 * B3 / M09 — Tests de SuspensionService (REGLA-07: feliz, error normativo, borde).
 */
@ExtendWith(MockitoExtension.class)
class SuspensionServiceTest {

    @Mock private SuspensionRepository repository;
    @Mock private CatSuspensionSunatRepository catalogoRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private SuspensionService service;

    private CatSuspensionSunat cat(String cod) {
        CatSuspensionSunat c = new CatSuspensionSunat();
        c.setCodSuspension(cod);
        c.setDescripcion("Descanso Medico (dia 21 en adelante)");
        c.setTipoPlame("SUBSIDIADO");
        return c;
    }

    private SuspensionDto dto(String cod, LocalDate ini, LocalDate fin, int dias) {
        SuspensionDto d = new SuspensionDto();
        d.setEmpleadoId(1L);
        d.setCodSuspension(cod);
        d.setFechaInicio(ini);
        d.setFechaFin(fin);
        d.setDiasAfectos(dias);
        return d;
    }

    @Test
    void crearFelizPersisteYDevuelveResponse() {
        when(catalogoRepository.findById("03")).thenReturn(Optional.of(cat("03")));
        when(repository.findByEmpleadoIdAndEstadoOrderByFechaInicio(1L, "ACTIVO"))
                .thenReturn(List.of());
        when(repository.save(any(Suspension.class))).thenAnswer(inv -> {
            Suspension s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        SuspensionResponseDto out = service.crear(
                dto("03", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 9), 5));

        assertThat(out.getId()).isEqualTo(100L);
        assertThat(out.getCodSuspension()).isEqualTo("03");
        assertThat(out.getDiasAfectos()).isEqualTo(5);
        assertThat(out.getEstado()).isEqualTo("ACTIVO");
        assertThat(out.getDescripcionSuspension()).isEqualTo("Descanso Medico (dia 21 en adelante)");
    }

    @Test
    void crearConCodigoInexistenteLanzaNegocio() {
        when(catalogoRepository.findById("99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(
                dto("99", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 9), 5)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("catálogo SUNAT");
    }

    @Test
    void crearConSolapeDeFechasLanzaNegocio() {
        // Existe una suspensión activa 2026-03-03..2026-03-10; la nueva 03-05..03-09 solapa.
        Suspension existente = new Suspension();
        existente.setId(50L);
        existente.setEmpleadoId(1L);
        existente.setEstado("ACTIVO");
        existente.setFechaInicio(LocalDate.of(2026, 3, 3));
        existente.setFechaFin(LocalDate.of(2026, 3, 10));

        when(catalogoRepository.findById("03")).thenReturn(Optional.of(cat("03")));
        when(repository.findByEmpleadoIdAndEstadoOrderByFechaInicio(1L, "ACTIVO"))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.crear(
                dto("03", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 9), 5)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("solapa");
    }

    @Test
    void fechaFinAnteriorAInicioLanzaNegocio() {
        lenient().when(catalogoRepository.findById("03")).thenReturn(Optional.of(cat("03")));

        assertThatThrownBy(() -> service.crear(
                dto("03", LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 5), 5)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("fecha fin");
    }
}
