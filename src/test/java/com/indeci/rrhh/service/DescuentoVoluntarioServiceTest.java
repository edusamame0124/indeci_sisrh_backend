package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.ConceptoSinCodigoMefException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.DescuentoVoluntarioDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.DescuentoVoluntario;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.DescuentoVoluntarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 010 / M07 — Tests del servicio de descuentos voluntarios.
 *   - caso feliz: alta correcta, ESTADO ACTIVO, SISPER espejado del concepto
 *   - caso error normativo: concepto sin CODIGO_MEF (LEY-01)
 *   - caso error: concepto que no es de tipo DESCUENTO
 *   - caso borde: monto mensual <= 0
 */
@ExtendWith(MockitoExtension.class)
class DescuentoVoluntarioServiceTest {

    @Mock private DescuentoVoluntarioRepository repository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private DescuentoVoluntarioService service;

    private static final Long EMPLEADO_ID = 41L;
    private static final Long CONCEPTO_ID = 700L;

    @Test
    void guardar_caso_feliz_crea_descuento_activo_con_sisper_del_concepto() {
        when(conceptoRepository.findById(CONCEPTO_ID))
                .thenReturn(Optional.of(concepto("05310", "717", "DESCUENTO")));

        service.guardar(dto(150.0));

        ArgumentCaptor<DescuentoVoluntario> capt =
                ArgumentCaptor.forClass(DescuentoVoluntario.class);
        verify(repository).save(capt.capture());

        DescuentoVoluntario saved = capt.getValue();
        assertThat(saved.getEstado()).isEqualTo("ACTIVO");
        assertThat(saved.getCodigoSisper()).isEqualTo("717");      // espejo del concepto
        assertThat(saved.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(saved.getConceptoPlanillaId()).isEqualTo(CONCEPTO_ID);
        assertThat(saved.getMontoMensual()).isEqualTo(150.0);
    }

    @Test
    void guardar_concepto_sin_codigo_mef_lanza_excepcion() {
        when(conceptoRepository.findById(CONCEPTO_ID))
                .thenReturn(Optional.of(concepto(null, "717", "DESCUENTO")));

        assertThatThrownBy(() -> service.guardar(dto(150.0)))
                .isInstanceOf(ConceptoSinCodigoMefException.class);
    }

    @Test
    void guardar_concepto_que_no_es_descuento_lanza_negocio() {
        when(conceptoRepository.findById(CONCEPTO_ID))
                .thenReturn(Optional.of(concepto("00301", "001", "REMUNERATIVO")));

        assertThatThrownBy(() -> service.guardar(dto(150.0)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("DESCUENTO");
    }

    @Test
    void guardar_monto_cero_lanza_negocio() {
        when(conceptoRepository.findById(CONCEPTO_ID))
                .thenReturn(Optional.of(concepto("05310", "717", "DESCUENTO")));

        assertThatThrownBy(() -> service.guardar(dto(0.0)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("monto");
    }

    // ============================ HELPERS ============================

    private ConceptoPlanilla concepto(String codigoMef, String sisper, String tipo) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(CONCEPTO_ID);
        c.setCodigoMef(codigoMef);
        c.setCodigoSisper(sisper);
        c.setNombre("Concepto " + tipo);
        c.setTipoConcepto(tipo);
        c.setActivo(1);
        return c;
    }

    private DescuentoVoluntarioDto dto(Double monto) {
        DescuentoVoluntarioDto d = new DescuentoVoluntarioDto();
        d.setEmpleadoId(EMPLEADO_ID);
        d.setConceptoPlanillaId(CONCEPTO_ID);
        d.setMontoMensual(monto);
        d.setFechaInicio(LocalDate.of(2026, 1, 1));
        return d;
    }
}
