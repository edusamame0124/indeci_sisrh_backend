package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MetaCertificacionDto;
import com.indeci.rrhh.dto.SemaforoMetaDto;
import com.indeci.rrhh.dto.SemaforoPresupuestalDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MetaPresupuestal;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MetaPresupuestalRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;

/**
 * Spec 012 / C1 · P-05 — Tests del semáforo presupuestal.
 *   - meta con comprometido ≤ certificado → VERDE
 *   - meta con comprometido > certificado → ROJO + estado global ROJO
 *   - período inexistente → NegocioException (caso de error)
 *   - guardar = upsert: actualiza la existente y crea la nueva
 *   - empleado sin meta → agrupado en "(sin meta)" (caso de borde)
 */
@ExtendWith(MockitoExtension.class)
class MetaPresupuestalServiceTest {

    @Mock private MetaPresupuestalRepository metaRepository;
    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;

    @InjectMocks private MetaPresupuestalService service;

    private static final Long PER_ID = 1L;
    private static final String PER = "2026-05";

    private PeriodoPlanilla periodo() {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setId(PER_ID);
        p.setPeriodo(PER);
        return p;
    }

    private MovimientoPlanilla mov(Long empleadoId, double neto) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setEmpleadoId(empleadoId);
        m.setNetoPagar(neto);
        m.setActivo(1);
        return m;
    }

    private EmpleadoPlanilla planilla(String meta) {
        EmpleadoPlanilla e = new EmpleadoPlanilla();
        e.setMeta(meta);
        e.setCentroCosto("OGDN");
        e.setFuenteFinanciamiento("RO");
        return e;
    }

    private MetaPresupuestal cert(String meta, double monto) {
        MetaPresupuestal c = new MetaPresupuestal();
        c.setId(99L);
        c.setPeriodoId(PER_ID);
        c.setMeta(meta);
        c.setMontoCertificado(monto);
        c.setActivo(1);
        return c;
    }

    @Test
    void metaDentroDelTechoSaleVerde() {
        when(periodoRepository.findById(PER_ID)).thenReturn(Optional.of(periodo()));
        when(movimientoRepository.findByPeriodoAndActivo(PER, 1))
                .thenReturn(List.of(mov(10L, 900d), mov(20L, 1800d)));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(10L, 1))
                .thenReturn(Optional.of(planilla("0056")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(20L, 1))
                .thenReturn(Optional.of(planilla("0056")));
        when(metaRepository.findByPeriodoIdAndActivo(PER_ID, 1))
                .thenReturn(List.of(cert("0056", 5000d)));

        SemaforoPresupuestalDto dto = service.semaforo(PER_ID);

        assertThat(dto.getMetas()).hasSize(1);
        SemaforoMetaDto fila = dto.getMetas().get(0);
        assertThat(fila.getMeta()).isEqualTo("0056");
        assertThat(fila.getPea()).isEqualTo(2);
        assertThat(fila.getMontoComprometido()).isEqualTo(2700d);
        assertThat(fila.getMontoCertificado()).isEqualTo(5000d);
        assertThat(fila.getSaldo()).isEqualTo(2300d);
        assertThat(fila.getEstado()).isEqualTo("VERDE");
        assertThat(dto.getEstadoGlobal()).isEqualTo("VERDE");
    }

    @Test
    void metaQueSuperaElTechoSaleRoja() {
        when(periodoRepository.findById(PER_ID)).thenReturn(Optional.of(periodo()));
        when(movimientoRepository.findByPeriodoAndActivo(PER, 1))
                .thenReturn(List.of(mov(10L, 900d), mov(20L, 1800d)));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(10L, 1))
                .thenReturn(Optional.of(planilla("0056")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(20L, 1))
                .thenReturn(Optional.of(planilla("0056")));
        when(metaRepository.findByPeriodoIdAndActivo(PER_ID, 1))
                .thenReturn(List.of(cert("0056", 2000d)));

        SemaforoPresupuestalDto dto = service.semaforo(PER_ID);

        SemaforoMetaDto fila = dto.getMetas().get(0);
        assertThat(fila.getEstado()).isEqualTo("ROJO");
        assertThat(fila.getSaldo()).isEqualTo(-700d);
        assertThat(dto.getEstadoGlobal()).isEqualTo("ROJO");
    }

    @Test
    void periodoInexistenteLanzaNegocioException() {
        when(periodoRepository.findById(PER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.semaforo(PER_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Período no encontrado");
    }

    @Test
    void guardarHaceUpsertActualizaLaExistenteYCreaLaNueva() {
        when(periodoRepository.findById(PER_ID)).thenReturn(Optional.of(periodo()));
        MetaPresupuestal existente = cert("0056", 1000d);
        when(metaRepository.findByPeriodoIdAndMetaAndActivo(PER_ID, "0056", 1))
                .thenReturn(Optional.of(existente));
        when(metaRepository.findByPeriodoIdAndMetaAndActivo(PER_ID, "0099", 1))
                .thenReturn(Optional.empty());

        MetaCertificacionDto d1 = new MetaCertificacionDto();
        d1.setMeta("0056");
        d1.setMontoCertificado(3000d);
        MetaCertificacionDto d2 = new MetaCertificacionDto();
        d2.setMeta("0099");
        d2.setMontoCertificado(1000d);

        service.guardar(PER_ID, List.of(d1, d2));

        ArgumentCaptor<MetaPresupuestal> captor = ArgumentCaptor.forClass(MetaPresupuestal.class);
        verify(metaRepository, times(2)).save(captor.capture());
        List<MetaPresupuestal> guardadas = captor.getAllValues();

        MetaPresupuestal actualizada = guardadas.get(0);
        assertThat(actualizada.getId()).isEqualTo(99L);
        assertThat(actualizada.getMontoCertificado()).isEqualTo(3000d);
        assertThat(actualizada.getUpdatedAt()).isNotNull();

        MetaPresupuestal nueva = guardadas.get(1);
        assertThat(nueva.getId()).isNull();
        assertThat(nueva.getMeta()).isEqualTo("0099");
        assertThat(nueva.getPeriodoId()).isEqualTo(PER_ID);
        assertThat(nueva.getActivo()).isEqualTo(1);
    }

    @Test
    void empleadoSinMetaSeAgrupaEnSinMeta() {
        when(periodoRepository.findById(PER_ID)).thenReturn(Optional.of(periodo()));
        when(movimientoRepository.findByPeriodoAndActivo(PER, 1))
                .thenReturn(List.of(mov(30L, 500d)));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(30L, 1))
                .thenReturn(Optional.empty());
        when(metaRepository.findByPeriodoIdAndActivo(PER_ID, 1)).thenReturn(List.of());

        SemaforoPresupuestalDto dto = service.semaforo(PER_ID);

        assertThat(dto.getMetas()).hasSize(1);
        SemaforoMetaDto fila = dto.getMetas().get(0);
        assertThat(fila.getMeta()).isEqualTo("(sin meta)");
        assertThat(fila.getMontoComprometido()).isEqualTo(500d);
        assertThat(fila.getMontoCertificado()).isEqualTo(0d);
        assertThat(fila.getEstado()).isEqualTo("ROJO");
    }
}
