package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PapeletaJustificacionResolverTest {

    private final PapeletaJustificacionResolver resolver =
            new PapeletaJustificacionResolver(Mockito.mock(SolicitudRrhhRepository.class));

    private SolicitudRrhh papeleta(String codigo, String nombre, LocalDate ini, LocalDate fin) {
        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo(codigo);
        tipo.setNombre(nombre);
        tipo.setJustificaAsistencia(1);

        SolicitudRrhh s = new SolicitudRrhh();
        s.setId(7L);
        s.setFechaInicio(ini);
        s.setFechaFin(fin);
        s.setTipoSolicitud(tipo);
        return s;
    }

    @Test
    void teletrabajo_aprobado_que_cubre_la_fecha_produce_dia_TELETRABAJO() {
        SolicitudRrhh p = papeleta("TELETRABAJO", "Reporte de Teletrabajo",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        Optional<AsistenciaDiaDto> dia = resolver.justificar(LocalDate.of(2026, 6, 10), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("TELETRABAJO");
        assertThat(dia.get().getOrigen()).isEqualTo("PAPELETA");
        assertThat(dia.get().getObservacion()).contains("Ley N° 31572");
    }

    @Test
    void permiso_con_goce_que_cubre_la_fecha_produce_dia_PERMISO() {
        SolicitudRrhh p = papeleta("008", "Permiso por lactancia",
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10));

        Optional<AsistenciaDiaDto> dia = resolver.justificar(LocalDate.of(2026, 6, 10), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("PERMISO");
        assertThat(dia.get().getObservacion()).contains("Permiso por lactancia");
    }

    @Test
    void fecha_fin_nula_solo_cubre_el_dia_de_inicio() {
        SolicitudRrhh p = papeleta("TELETRABAJO", "Reporte de Teletrabajo",
                LocalDate.of(2026, 6, 10), null);

        assertThat(resolver.justificar(LocalDate.of(2026, 6, 10), List.of(p))).isPresent();
        assertThat(resolver.justificar(LocalDate.of(2026, 6, 11), List.of(p))).isEmpty();
    }

    @Test
    void fecha_fuera_del_rango_no_justifica() {
        SolicitudRrhh p = papeleta("TELETRABAJO", "Reporte de Teletrabajo",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

        assertThat(resolver.justificar(LocalDate.of(2026, 6, 10), List.of(p))).isEmpty();
    }

    @Test
    void sin_papeletas_no_justifica() {
        assertThat(resolver.justificar(LocalDate.of(2026, 6, 10), List.of())).isEmpty();
    }
}
