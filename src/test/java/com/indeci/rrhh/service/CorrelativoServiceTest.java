package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.entity.CorrelativoDocumento;
import com.indeci.rrhh.repository.CorrelativoDocumentoRepository;

/**
 * B3 / M14 — Tests de la lógica de incremento del correlativo (Mockito).
 * La serialización real bajo concurrencia (lock) se valida en el @DataJpaTest del repo.
 */
@ExtendWith(MockitoExtension.class)
class CorrelativoServiceTest {

    @Mock private CorrelativoDocumentoRepository repository;
    @InjectMocks private CorrelativoService service;

    @Test
    void primeraVezCreaLaFilaYDevuelveUno() {
        when(repository.findByCodEntidadAndAnioAndMesAndTipoDocumento(
                "000009", 2026, 3, "MCPP_03")).thenReturn(Optional.empty());
        when(repository.save(any(CorrelativoDocumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        long nro = service.siguiente("000009", 2026, 3, "MCPP_03");

        assertThat(nro).isEqualTo(1L);
    }

    @Test
    void filaExistenteIncrementaDesdeUltimoNro() {
        CorrelativoDocumento existente = new CorrelativoDocumento();
        existente.setCodEntidad("000009");
        existente.setAnio(2026);
        existente.setMes(3);
        existente.setTipoDocumento("MCPP_03");
        existente.setUltimoNro(37L);
        when(repository.findByCodEntidadAndAnioAndMesAndTipoDocumento(
                "000009", 2026, 3, "MCPP_03")).thenReturn(Optional.of(existente));
        when(repository.save(any(CorrelativoDocumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        long nro = service.siguiente("000009", 2026, 3, "MCPP_03");

        assertThat(nro).isEqualTo(38L);
        assertThat(existente.getUltimoNro()).isEqualTo(38L);
    }

    @Test
    void tiposDistintosLlevanCorrelativosIndependientes() {
        CorrelativoDocumento servir = new CorrelativoDocumento();
        servir.setUltimoNro(24L);
        CorrelativoDocumento cas = new CorrelativoDocumento();
        cas.setUltimoNro(38L);
        when(repository.findByCodEntidadAndAnioAndMesAndTipoDocumento(
                "000009", 2026, 3, "MCPP_01")).thenReturn(Optional.of(servir));
        when(repository.findByCodEntidadAndAnioAndMesAndTipoDocumento(
                "000009", 2026, 3, "MCPP_03")).thenReturn(Optional.of(cas));
        when(repository.save(any(CorrelativoDocumento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.siguiente("000009", 2026, 3, "MCPP_01")).isEqualTo(25L);
        assertThat(service.siguiente("000009", 2026, 3, "MCPP_03")).isEqualTo(39L);
    }
}
