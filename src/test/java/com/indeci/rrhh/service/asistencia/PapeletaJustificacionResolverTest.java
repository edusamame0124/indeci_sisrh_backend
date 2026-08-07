package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoLicencia;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoLicenciaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PapeletaJustificacionResolverTest {

    private final TipoLicenciaRepository tipoLicenciaRepository = Mockito.mock(TipoLicenciaRepository.class);
    private final SolicitudRrhhRepository solicitudRrhhRepository =
            Mockito.mock(SolicitudRrhhRepository.class);

    private final PapeletaJustificacionResolver resolver =
            new PapeletaJustificacionResolver(solicitudRrhhRepository, tipoLicenciaRepository);

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

    private SolicitudRrhh papeletaLicencia(Long tipoLicenciaId, boolean sinGoce, LocalDate ini, LocalDate fin) {
        SolicitudRrhh s = papeleta("011", "LICENCIA", ini, fin);
        s.setTipoLicenciaId(tipoLicenciaId);

        TipoLicencia tl = new TipoLicencia();
        tl.setId(tipoLicenciaId);
        tl.setEsSinGoce(sinGoce ? 1 : 0);
        when(tipoLicenciaRepository.findById(tipoLicenciaId)).thenReturn(Optional.of(tl));
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

    @Test
    void vacaciones_aprobadas_que_cubre_la_fecha_produce_dia_VACACIONES_no_PERMISO() {
        SolicitudRrhh p = papeleta("012", "Solicitud de Vacaciones",
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 16));

        Optional<AsistenciaDiaDto> dia = resolver.justificar(LocalDate.of(2026, 7, 14), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("VACACIONES");
    }

    @Test
    void vacaciones_sobre_marcacion_real_sobrescribe_a_VACACIONES_con_rastro_en_observacion() {
        SolicitudRrhh p = papeleta("012", "Solicitud de Vacaciones",
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 16));

        Optional<AsistenciaDiaDto> dia =
                resolver.justificarVacacionSobreMarcacion(LocalDate.of(2026, 7, 14), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("VACACIONES");
        assertThat(dia.get().getOrigen()).isEqualTo("PAPELETA");
        assertThat(dia.get().getObservacion())
                .contains("Se ignoró marcación física por papeleta aprobada: Solicitud de Vacaciones");
    }

    @Test
    void vacaciones_sobre_marcacion_no_aplica_a_teletrabajo() {
        SolicitudRrhh p = papeleta("TELETRABAJO", "Reporte de Teletrabajo",
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 16));

        assertThat(resolver.justificarVacacionSobreMarcacion(LocalDate.of(2026, 7, 14), List.of(p)))
                .isEmpty();
    }

    @Test
    void vacaciones_sobre_marcacion_fecha_fuera_de_rango_no_sobrescribe() {
        SolicitudRrhh p = papeleta("012", "Solicitud de Vacaciones",
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 16));

        assertThat(resolver.justificarVacacionSobreMarcacion(LocalDate.of(2026, 7, 20), List.of(p)))
                .isEmpty();
    }

    // ── Licencia (código 011) — caso real BALTAZAR FLORES (solicitud 323, con goce, "Por onomástico") ──

    @Test
    void licencia_con_goce_produce_dia_LICENCIA_con_observacion_CON_GOCE() {
        SolicitudRrhh p = papeletaLicencia(51L, false, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 9));

        Optional<AsistenciaDiaDto> dia = resolver.justificar(LocalDate.of(2026, 7, 9), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("LICENCIA");
        assertThat(dia.get().getOrigen()).isEqualTo("PAPELETA");
        assertThat(dia.get().getObservacion())
                .contains("Justificado por papeleta aprobada: LICENCIA CON GOCE");
    }

    @Test
    void licencia_sin_goce_produce_dia_LICENCIA_con_observacion_SIN_GOCE() {
        SolicitudRrhh p = papeletaLicencia(60L, true, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 9));

        Optional<AsistenciaDiaDto> dia = resolver.justificar(LocalDate.of(2026, 7, 9), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("LICENCIA");
        assertThat(dia.get().getObservacion())
                .contains("Justificado por papeleta aprobada: LICENCIA SIN GOCE");
    }

    @Test
    void licencia_sin_tipoLicenciaId_asume_con_goce_por_defecto() {
        SolicitudRrhh p = papeleta("011", "LICENCIA", LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 9));
        // tipoLicenciaId queda null a propósito: dato incompleto no debe reventar ni caer a SIN GOCE.

        Optional<AsistenciaDiaDto> dia = resolver.justificar(LocalDate.of(2026, 7, 9), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("LICENCIA");
        assertThat(dia.get().getObservacion()).contains("LICENCIA CON GOCE");
    }

    // ── Salida anticipada (Observado) — decisión RR.HH. 2026-08-07 ──

    @Test
    void permiso_aprobado_que_cubre_la_fecha_limpia_la_salida_anticipada_y_vuelve_a_LABORAL() {
        SolicitudRrhh p = papeleta("008", "Permiso por lactancia",
                LocalDate.of(2026, 7, 21), LocalDate.of(2026, 7, 21));

        Optional<AsistenciaDiaDto> dia =
                resolver.justificarSalidaAnticipada(LocalDate.of(2026, 7, 21), List.of(p));

        assertThat(dia).isPresent();
        assertThat(dia.get().getTipoDia()).isEqualTo("LABORAL");
        assertThat(dia.get().getMinutosSalidaAnticipada()).isZero();
        assertThat(dia.get().getMinutosTardanza()).isZero();
        assertThat(dia.get().getOrigen()).isEqualTo("PAPELETA");
        assertThat(dia.get().getObservacion())
                .contains("Salida anticipada justificada por papeleta aprobada: Permiso por lactancia");
    }

    @Test
    void sin_papeleta_que_cubra_la_fecha_no_justifica_la_salida_anticipada() {
        SolicitudRrhh p = papeleta("008", "Permiso por lactancia",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5));

        assertThat(resolver.justificarSalidaAnticipada(LocalDate.of(2026, 7, 21), List.of(p)))
                .isEmpty();
    }

    /**
     * Bug real encontrado en auditoría: Vacaciones ("012") y Omisión ("004") NUNCA tienen
     * JUSTIFICA_ASISTENCIA=1 (V012_36 — "tienen su propio tipo de día"). Si la consulta solo
     * filtrara por el flag, cargarJustificantes() jamás traería su propia papeleta y
     * justificarVacacionSobreMarcacion/justificarOmision nunca encontrarían nada que
     * reclasificar. Deben pedirse siempre, independientemente del flag.
     */
    @Test
    void cargarJustificantes_siempre_incluye_vacaciones_y_omision_sin_depender_del_flag() {
        LocalDate fin = LocalDate.of(2026, 7, 31);
        when(solicitudRrhhRepository.findJustificantesAsistencia(
                eq(99L), eq(9L), eq(fin), eq(List.of("012", "004"))))
                .thenReturn(List.of());

        resolver.cargarJustificantes(99L, fin);

        verify(solicitudRrhhRepository)
                .findJustificantesAsistencia(99L, 9L, fin, List.of("012", "004"));
    }
}
