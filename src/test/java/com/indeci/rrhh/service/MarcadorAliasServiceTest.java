package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MarcadorAliasDto;
import com.indeci.rrhh.dto.MarcadorAliasRequest;
import com.indeci.rrhh.dto.MarcadorSinMapeoDto;
import com.indeci.rrhh.entity.EmpleadoMarcadorAlias;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.EmpleadoMarcadorAliasRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.service.asistencia.NombreMarcadorNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarcadorAliasServiceTest {

    @Mock private AsistenciaImportacionFilaRepository filaRepository;
    @Mock private EmpleadoMarcadorAliasRepository aliasRepository;
    @Mock private EmpleadoRepository empleadoRepository;

    @InjectMocks private MarcadorAliasService service;

    @Test
    void listarSinMapeo_mapeaNombreYCantidadDeDias() {
        when(filaRepository.resumenSinMapeo(1L)).thenReturn(List.of(
                new Object[]{"AGUIRRE SAENZ, HUGO RAFAEL", 16L},
                new Object[]{"ALBINES GARCIA, PERCY", 8L}));

        List<MarcadorSinMapeoDto> res = service.listarSinMapeo(1L);

        assertThat(res).hasSize(2);
        assertThat(res.get(0).getNombreMarcador()).isEqualTo("AGUIRRE SAENZ, HUGO RAFAEL");
        assertThat(res.get(0).getDias()).isEqualTo(16);
    }

    @Test
    void mapear_creaAliasNuevoNormalizado() {
        MarcadorAliasRequest req = new MarcadorAliasRequest();
        req.setEmpleadoId(42L);
        req.setNombreMarcador("Aguirre Saenz, Hugo Rafael");

        String norm = NombreMarcadorNormalizer.normalizar("Aguirre Saenz, Hugo Rafael");
        when(empleadoRepository.existsById(42L)).thenReturn(true);
        when(aliasRepository.findFirstByNombreMarcadorNormAndActivo(norm, 1))
                .thenReturn(Optional.empty());
        when(aliasRepository.save(any(EmpleadoMarcadorAlias.class))).thenAnswer(inv -> {
            EmpleadoMarcadorAlias a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        MarcadorAliasDto dto = service.mapear(req);

        assertThat(dto.getId()).isEqualTo(100L);
        assertThat(dto.getEmpleadoId()).isEqualTo(42L);
        assertThat(dto.getNombreMarcadorNorm()).isEqualTo(norm);
        assertThat(dto.getOrigen()).isEqualTo("COEN");
    }

    @Test
    void mapear_sinEmpleadoId_lanzaError() {
        MarcadorAliasRequest req = new MarcadorAliasRequest();
        req.setNombreMarcador("AGUIRRE SAENZ, HUGO RAFAEL");

        assertThatThrownBy(() -> service.mapear(req))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("empleado");
    }

    @Test
    void mapear_empleadoInexistente_lanzaError() {
        MarcadorAliasRequest req = new MarcadorAliasRequest();
        req.setEmpleadoId(999L);
        req.setNombreMarcador("AGUIRRE SAENZ, HUGO RAFAEL");
        when(empleadoRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.mapear(req))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no existe");
    }
}
