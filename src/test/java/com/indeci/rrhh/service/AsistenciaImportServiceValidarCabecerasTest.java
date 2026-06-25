package com.indeci.rrhh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.dto.AsistenciaValidacionBatchDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvParser;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvValidator;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresCsvWriter;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaImportServiceValidarCabecerasTest {

    @Mock private AsistenciaCsvParser csvParser;
    @Mock private AsistenciaCsvValidator csvValidator;
    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private AsistenciaCabeceraRepository cabeceraRepository;
    @Mock private AsistenciaImportacionRepository importacionRepository;
    @Mock private AsistenciaImportacionFilaRepository filaRepository;
    @Mock private BaseAsistenciaResolver baseResolver;
    @Mock private AsistenciaService asistenciaService;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private AsistenciaImportErroresCsvWriter erroresCsvWriter;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private JornadaRegimenRepository jornadaRegimenRepository;
    @Mock private AsistenciaDetalleRepository detalleRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private AsistenciaImportService service;

    @Test
    void validarCabeceras_soloValidaPrevalidadaYListaParaValidar() {
        AsistenciaImportacion importacion = new AsistenciaImportacion();
        importacion.setId(77L);
        importacion.setPeriodo("2026-06");

        AsistenciaCabecera prevalidada = cabecera("PREVALIDADA");
        AsistenciaCabecera lista = cabecera("LISTA_PARA_VALIDAR");
        AsistenciaCabecera observada = cabecera("OBSERVADA");
        AsistenciaCabecera validada = cabecera("VALIDADA");

        when(importacionRepository.findById(77L)).thenReturn(Optional.of(importacion));
        when(cabeceraRepository.findByImportacionIdAndActivo(77L, 1))
                .thenReturn(List.of(prevalidada, lista, observada, validada));

        AsistenciaValidacionBatchDto result = service.validarCabeceras(77L);

        assertThat(prevalidada.getEstado()).isEqualTo("VALIDADA");
        assertThat(lista.getEstado()).isEqualTo("VALIDADA");
        assertThat(observada.getEstado()).isEqualTo("OBSERVADA");
        assertThat(validada.getEstado()).isEqualTo("VALIDADA");
        assertThat(result.getTotalCabeceras()).isEqualTo(4);
        assertThat(result.getValidadas()).isEqualTo(2);
        assertThat(result.getObservadas()).isEqualTo(1);
        assertThat(result.getYaValidadas()).isEqualTo(1);
        verify(cabeceraRepository).saveAll(List.of(prevalidada, lista, observada, validada));
    }

    @Test
    void validarCabeceras_refrescaDescuentosConBaseVigente() {
        AsistenciaImportacion importacion = new AsistenciaImportacion();
        importacion.setId(77L);
        importacion.setPeriodo("2026-06");

        AsistenciaCabecera cab = cabecera("PREVALIDADA");
        cab.setId(500L);
        cab.setEmpleadoId(42L);
        cab.setDiasFalta(1);
        cab.setDescuentoTardanza(0.0); // estaba en 0 porque la base era 0 al importar
        cab.setDescuentoFalta(0.0);

        when(importacionRepository.findById(77L)).thenReturn(Optional.of(importacion));
        when(cabeceraRepository.findByImportacionIdAndActivo(77L, 1)).thenReturn(List.of(cab));

        // Jornada + un día de 45 min (bruto) → Descuento 1 (45 > umbral 10).
        EmpleadoPlanilla ep = new EmpleadoPlanilla();
        ep.setRegimenLaboralId(7L);
        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(42L, 1))
                .thenReturn(Optional.of(ep));
        JornadaRegimen jornada = new JornadaRegimen();
        jornada.setHoraIngreso("08:00");
        when(jornadaRegimenRepository.findByRegimenLaboralId(7L)).thenReturn(Optional.of(jornada));
        AsistenciaDetalle d = new AsistenciaDetalle();
        d.setTipoDia("LABORAL");
        d.setMarcaEntrada("08:45"); // 45 min bruto
        when(detalleRepository.findByCabeceraIdOrderByDia(500L)).thenReturn(List.of(d));

        BaseAsistenciaResult base = new BaseAsistenciaResult();
        base.setRemuneracionBase(3000.0);
        when(baseResolver.resolver(42L)).thenReturn(base);

        service.validarCabeceras(77L);

        assertThat(cab.getEstado()).isEqualTo("VALIDADA");
        assertThat(cab.getRemuneracionBase()).isEqualTo(3000.0);
        assertThat(cab.getDescuentoTardanza()).isEqualTo(9.38);       // 45 min D1: ROUND(3000*45/14400,2)
        assertThat(cab.getDescuentoTardanzaDiaria()).isEqualTo(9.38);
        assertThat(cab.getDescuentoFalta()).isEqualTo(100.0);         // ROUND(3000/30,2)
    }

    @Test
    void validarCabeceras_recalculaTardanzaDesdeMarcasYJornada() {
        AsistenciaImportacion importacion = new AsistenciaImportacion();
        importacion.setId(77L);
        importacion.setPeriodo("2026-06");

        AsistenciaCabecera cab = cabecera("PREVALIDADA");
        cab.setId(500L);
        cab.setEmpleadoId(42L);
        cab.setTotalMinTardanza(0); // venía en 0 (importado antes de configurar la jornada)

        when(importacionRepository.findById(77L)).thenReturn(Optional.of(importacion));
        when(cabeceraRepository.findByImportacionIdAndActivo(77L, 1)).thenReturn(List.of(cab));

        // Régimen del empleado → jornada CAS: ingreso 08:30 (umbral/tope/jornada
        // por defecto 10/60/8). Modelo dos niveles: NO se resta tolerancia.
        EmpleadoPlanilla ep = new EmpleadoPlanilla();
        ep.setRegimenLaboralId(7L);
        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(42L, 1))
                .thenReturn(Optional.of(ep));
        JornadaRegimen jornada = new JornadaRegimen();
        jornada.setHoraIngreso("08:30");
        when(jornadaRegimenRepository.findByRegimenLaboralId(7L)).thenReturn(Optional.of(jornada));

        // Día LABORAL con Marca1 = 08:50 → 08:50 − 08:30 = 20 min (bruto, sin tolerancia).
        AsistenciaDetalle d = new AsistenciaDetalle();
        d.setTipoDia("LABORAL");
        d.setMarcaEntrada("08:50");
        when(detalleRepository.findByCabeceraIdOrderByDia(500L)).thenReturn(List.of(d));

        BaseAsistenciaResult base = new BaseAsistenciaResult();
        base.setRemuneracionBase(3000.0);
        when(baseResolver.resolver(42L)).thenReturn(base);

        service.validarCabeceras(77L);

        assertThat(d.getMinutosTardanza()).isEqualTo(20);
        assertThat(d.getTipoDia()).isEqualTo("TARDANZA");
        assertThat(cab.getTotalMinTardanza()).isEqualTo(20);
        // 20 min > umbral 10 → Descuento 1. ROUND((3000*20)/(30*8*60), 2) = 4.17
        assertThat(cab.getMinTardanzaDiaria()).isEqualTo(20);
        assertThat(cab.getDescuentoTardanza()).isEqualTo(4.17);
        assertThat(cab.getDescuentoTardanzaDiaria()).isEqualTo(4.17);
    }

    private AsistenciaCabecera cabecera(String estado) {
        AsistenciaCabecera cabecera = new AsistenciaCabecera();
        cabecera.setEstado(estado);
        cabecera.setActivo(1);
        return cabecera;
    }
}
