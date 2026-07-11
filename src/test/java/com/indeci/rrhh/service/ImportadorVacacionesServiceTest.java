package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.dto.ImportacionVacacionesResultDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

/**
 * SPEC_VACACIONES F8 — ImportadorVacacionesService. Construye un .xlsx en memoria
 * (hoja DATOS) y verifica parse → resolución de DNI → upsert de la línea base.
 */
@ExtendWith(MockitoExtension.class)
class ImportadorVacacionesServiceTest {

    @Mock private PersonaRepository personaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private VacacionSaldoRepository vacacionSaldoRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private ImportadorVacacionesService service;

    private byte[] construirXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("DATOS");
            // Fila 0: cabecera (se ignora).
            s.createRow(0).createCell(0).setCellValue("N°");
            // Fila 1: empleado existente. B=DNI, C=nombre, K=fecha corte, P=ganados, Q=gozados.
            Row r1 = s.createRow(1);
            r1.createCell(1).setCellValue(44552584);       // B
            r1.createCell(2).setCellValue("ANA SALAS");    // C
            r1.createCell(10).setCellValue(LocalDate.of(2026, 5, 30)); // K
            r1.createCell(15).setCellValue(210);           // P
            r1.createCell(16).setCellValue(180);           // Q
            // Fila 2: DNI inexistente.
            Row r2 = s.createRow(2);
            r2.createCell(1).setCellValue(99999999);
            r2.createCell(2).setCellValue("FANTASMA");
            r2.createCell(10).setCellValue(LocalDate.of(2026, 5, 30));
            r2.createCell(15).setCellValue(30);
            r2.createCell(16).setCellValue(0);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void importar_upsert_baseline_y_reporta_no_encontrados() throws Exception {
        Persona persona = new Persona();
        persona.setId(1L);
        when(personaRepository.findByDniNormalizado("44552584")).thenReturn(Optional.of(persona));
        when(personaRepository.findByDniNormalizado("99999999")).thenReturn(Optional.empty());

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        when(empleadoRepository.findByPersonaId(1L)).thenReturn(Optional.of(empleado));

        when(vacacionSaldoRepository.findByEmpleadoIdAndAnioAndActivo(42L, 2026, 1))
                .thenReturn(Optional.empty());

        ImportacionVacacionesResultDto res = service.importar(construirXlsx());

        assertThat(res.totalFilas()).isEqualTo(2);
        assertThat(res.importados()).isEqualTo(1);
        assertThat(res.noEncontrados()).hasSize(1);
        assertThat(res.noEncontrados().get(0)).contains("99999999");
        assertThat(res.fechaCorte()).isEqualTo("2026-05-30");
        assertThat(res.origen()).isEqualTo(ImportadorVacacionesService.ORIGEN_MIGRACION);

        ArgumentCaptor<VacacionSaldo> capt = ArgumentCaptor.forClass(VacacionSaldo.class);
        org.mockito.Mockito.verify(vacacionSaldoRepository).save(capt.capture());
        VacacionSaldo guardado = capt.getValue();
        assertThat(guardado.getEmpleadoId()).isEqualTo(42L);
        assertThat(guardado.getAnio()).isEqualTo(2026);
        assertThat(guardado.getDiasGanados()).isEqualTo(210d);
        assertThat(guardado.getDiasGozados()).isEqualTo(180d);
        assertThat(guardado.getOrigen()).isEqualTo(ImportadorVacacionesService.ORIGEN_MIGRACION);
        assertThat(guardado.getFechaCorte()).isEqualTo(LocalDate.of(2026, 5, 30));
    }
}
