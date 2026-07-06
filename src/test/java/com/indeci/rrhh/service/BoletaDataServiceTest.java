package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.rrhh.dto.BoletaPagoResponseDto;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

/** Track B — Boleta consolidada (opción A): casos a/b/c + neto total con judicial. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoletaDataServiceTest {

    private static final Long EMP = 1L;
    private static final String PERIODO = "2026-07";

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private MovimientoPlanillaDetalleRepository detalleRepository;
    @Mock private EmpleadoRepository empleadoRepository;

    @InjectMocks private BoletaDataService service;

    private MovimientoPlanilla mov(long id, String tipo, double ing, double desc, double neto) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(id);
        m.setEmpleadoId(EMP);
        m.setPeriodo(PERIODO);
        m.setTipoPlanilla(tipo);
        m.setTotalIngresos(ing);
        m.setTotalDescuentos(desc);
        m.setNetoPagar(neto);
        m.setActivo(1);
        return m;
    }

    private MovimientoPlanillaDetalle det(String tipoConcepto, String codigo, String nombre, double monto) {
        MovimientoPlanillaDetalle d = new MovimientoPlanillaDetalle();
        d.setConceptoTipo(tipoConcepto);
        d.setConceptoCodigo(codigo);
        d.setConceptoNombre(nombre);
        d.setMonto(monto);
        return d;
    }

    @Test
    void caso_a_regular_mas_aguinaldo_arma_ambas_secciones() {
        MovimientoPlanilla reg = mov(1L, "ORDINARIA", 4864.19, 563.06, 4301.13);
        MovimientoPlanilla agui = mov(2L, "AGUINALDO", 5364.19, 0.0, 5364.19);
        when(movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(EMP, PERIODO, 1))
                .thenReturn(List.of(reg, agui));
        when(detalleRepository.findByMovimientoPlanillaId(1L))
                .thenReturn(List.of(det("REMUNERATIVO", "027", "HONOR. CAS", 4500.00)));
        when(detalleRepository.findByMovimientoPlanillaId(2L))
                .thenReturn(List.of(det("NO_REMUNERATIVO", "0077", "Aguinaldo CAS", 5364.19)));

        BoletaPagoResponseDto dto = service.obtenerBoletaData(EMP, PERIODO);

        assertThat(dto.getIngresos()).hasSize(1);            // sección regular
        assertThat(dto.getNetoPagar()).isEqualTo(4301.13);
        assertThat(dto.getAguinaldo()).isNotNull();
        assertThat(dto.getAguinaldo().getIngresos()).hasSize(1);
        assertThat(dto.getAguinaldo().getDescuentos()).isEmpty();
        assertThat(dto.getAguinaldo().getNeto()).isEqualTo(5364.19);
        assertThat(dto.getNetoTotal()).isEqualTo(9665.32);  // 4301.13 + 5364.19
    }

    @Test
    void caso_b_solo_regular_boleta_identica_sin_seccion_aguinaldo() {
        MovimientoPlanilla reg = mov(1L, "ORDINARIA", 4864.19, 563.06, 4301.13);
        when(movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(EMP, PERIODO, 1))
                .thenReturn(List.of(reg));
        when(detalleRepository.findByMovimientoPlanillaId(1L))
                .thenReturn(List.of(det("REMUNERATIVO", "027", "HONOR. CAS", 4500.00)));

        BoletaPagoResponseDto dto = service.obtenerBoletaData(EMP, PERIODO);

        assertThat(dto.getAguinaldo()).isNull();             // sin sección aguinaldo
        assertThat(dto.getIngresos()).hasSize(1);
        assertThat(dto.getNetoPagar()).isEqualTo(4301.13);
        assertThat(dto.getNetoTotal()).isEqualTo(4301.13);   // = neto regular
    }

    @Test
    void caso_c_solo_aguinaldo_seccion_regular_vacia() {
        MovimientoPlanilla agui = mov(2L, "AGUINALDO", 5364.19, 0.0, 5364.19);
        when(movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(EMP, PERIODO, 1))
                .thenReturn(List.of(agui));
        when(detalleRepository.findByMovimientoPlanillaId(2L))
                .thenReturn(List.of(det("NO_REMUNERATIVO", "0077", "Aguinaldo CAS", 5364.19)));

        BoletaPagoResponseDto dto = service.obtenerBoletaData(EMP, PERIODO);

        assertThat(dto.getIngresos()).isEmpty();             // sección regular vacía
        assertThat(dto.getNetoPagar()).isEqualTo(0.0);
        assertThat(dto.getAguinaldo()).isNotNull();
        assertThat(dto.getAguinaldo().getIngresos()).hasSize(1);
        assertThat(dto.getNetoTotal()).isEqualTo(5364.19);
    }

    @Test
    void caso_a_con_descuento_judicial_verifica_neto_total() {
        MovimientoPlanilla reg = mov(1L, "ORDINARIA", 4864.19, 563.06, 4301.13);
        MovimientoPlanilla agui = mov(2L, "AGUINALDO", 5364.19, 1609.26, 3754.93);
        when(movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(EMP, PERIODO, 1))
                .thenReturn(List.of(reg, agui));
        when(detalleRepository.findByMovimientoPlanillaId(1L))
                .thenReturn(List.of(det("REMUNERATIVO", "027", "HONOR. CAS", 4500.00)));
        when(detalleRepository.findByMovimientoPlanillaId(2L))
                .thenReturn(List.of(
                        det("NO_REMUNERATIVO", "0077", "Aguinaldo CAS", 5364.19),
                        det("DESCUENTO_JUDICIAL", "716", "Retención judicial 30%", 1609.26)));

        BoletaPagoResponseDto dto = service.obtenerBoletaData(EMP, PERIODO);

        assertThat(dto.getAguinaldo().getDescuentos()).hasSize(1);
        assertThat(dto.getAguinaldo().getDescuentos().get(0).getMonto()).isEqualTo(1609.26);
        assertThat(dto.getAguinaldo().getNeto()).isEqualTo(3754.93);
        assertThat(dto.getNetoTotal()).isEqualTo(8056.06);   // 4301.13 + 3754.93
    }
}
