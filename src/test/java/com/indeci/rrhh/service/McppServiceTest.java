package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.Entidad;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EntidadRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

/**
 * B3 / M14 — Tests de McppService (CAS feliz, judicial tipo 12, exclusión 0210 en 03).
 */
@ExtendWith(MockitoExtension.class)
class McppServiceTest {

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private MovimientoPlanillaDetalleRepository detalleRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private EntidadRepository entidadRepository;
    @Mock private PersonaService personaService;
    @Mock private CorrelativoService correlativoService;

    @InjectMocks private McppService service;

    private ConceptoPlanilla concepto(long id, String mcpp, String tipo, String nombre) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(id);
        c.setCodigoMcpp(mcpp);
        c.setTipo(tipo);
        c.setNombre(nombre);
        return c;
    }

    private MovimientoPlanillaDetalle det(long conceptoId, double monto) {
        MovimientoPlanillaDetalle d = new MovimientoPlanillaDetalle();
        d.setConceptoPlanillaId(conceptoId);
        d.setMonto(monto);
        return d;
    }

    @BeforeEach
    void comun() {
        Entidad ent = new Entidad();
        ent.setCodEntidad("000009");
        lenient().when(entidadRepository.findAll()).thenReturn(List.of(ent));

        RegimenLaboral cas = new RegimenLaboral();
        cas.setId(4L);
        cas.setCodigo("CAS");
        lenient().when(regimenLaboralRepository.findAll()).thenReturn(List.of(cas));

        EmpleadoPlanilla ep = new EmpleadoPlanilla();
        ep.setEmpleadoId(1L);
        ep.setRegimenLaboralId(4L);
        lenient().when(empleadoPlanillaRepository.findByActivo(1)).thenReturn(List.of(ep));

        Empleado emp = new Empleado();
        emp.setId(1L);
        emp.setRegistroAirhsp("000674");
        lenient().when(empleadoRepository.findAll()).thenReturn(List.of(emp));

        PersonaEmpleadoResponseDto persona = new PersonaEmpleadoResponseDto();
        persona.setEmpleadoId(1L);
        persona.setDni("00256418");
        lenient().when(personaService.listar()).thenReturn(List.of(persona));

        MovimientoPlanilla mov = new MovimientoPlanilla();
        mov.setId(100L);
        mov.setEmpleadoId(1L);
        lenient().when(movimientoRepository.findByPeriodoAndActivo("2026-04", 1))
                .thenReturn(List.of(mov));

        lenient().when(correlativoService.siguiente(eq("000009"), anyInt(), anyInt(), eq("MCPP")))
                .thenReturn(38L);
    }

    @Test
    void casTipo03GeneraCabeceraYDetalles() {
        when(conceptoRepository.findAll()).thenReturn(List.of(
                concepto(10L, "0131", "INGRESO", "HONORARIOS CAS"),
                concepto(11L, "0009", "DESCUENTO", "AP.OBLIG.CAS")));
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of(
                det(10L, 2264.19), det(11L, 236.42)));

        McppService.McppArchivo out = service.generar("2026-04", "03");

        assertThat(out.nombreArchivo()).isEqualTo("PLL00000920260401030038.TXT");
        assertThat(out.totalRegistros()).isEqualTo(2);
        assertThat(out.contenido()).isEqualTo(
                "000009|2026|04|01|03|0038|2|2264.19|236.42|0.00\r\n"
                + "2|00256418|00|1|0131|HONORARIOS CAS|2264.19|4|000674\r\n"
                + "2|00256418|00|2|0009|AP.OBLIG.CAS|236.42|4|000674\r\n");
    }

    @Test
    void tipo03ExcluyeMandatoJudicial0210() {
        when(conceptoRepository.findAll()).thenReturn(List.of(
                concepto(10L, "0131", "INGRESO", "HONORARIOS CAS"),
                concepto(12L, "0210", "DESCUENTO", "Monto Mandato judicial")));
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of(
                det(10L, 2264.19), det(12L, 400.00)));

        McppService.McppArchivo out = service.generar("2026-04", "03");

        assertThat(out.totalRegistros()).isEqualTo(1);
        assertThat(out.contenido()).contains("|0131|");
        assertThat(out.contenido()).doesNotContain("|0210|");
    }

    @Test
    void tipo12SoloIncluyeMandatoJudicial0210() {
        when(conceptoRepository.findAll()).thenReturn(List.of(
                concepto(10L, "0131", "INGRESO", "HONORARIOS CAS"),
                concepto(12L, "0210", "DESCUENTO", "Monto Mandato judicial")));
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of(
                det(10L, 2264.19), det(12L, 400.00)));

        McppService.McppArchivo out = service.generar("2026-04", "12");

        assertThat(out.totalRegistros()).isEqualTo(1);
        // tipoDoc=04 para judicial, tipoPlanilla=12
        assertThat(out.contenido()).startsWith("000009|2026|04|04|12|0038|1|");
        assertThat(out.contenido()).contains(
                "2|00256418|00|2|0210|Monto Mandato judicial|400.00|4|000674\r\n");
        assertThat(out.nombreArchivo()).isEqualTo("PLL00000920260404120038.TXT");
    }
}
