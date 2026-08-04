package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.entity.TipoLicencia;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.TipoLicenciaRepository;

/**
 * Documento de sustento OBLIGATORIO (RR.HH., 2026-08-04) — guard nuevo y
 * acotado, solo para 'COMISION_DIA' y Licencia con goce (código '011',
 * esSinGoce=0). No reactiva la validación genérica retirada (validarSustento)
 * que afecta a los demás tipos de papeleta.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhSustentoObligatorioTest {

    private static final Long ID_TIPO_LICENCIA_CON_GOCE = 1L;
    private static final Long ID_TIPO_LICENCIA_SIN_GOCE = 2L;

    @Mock
    private TipoLicenciaRepository tipoLicenciaRepository;

    @InjectMocks
    private SolicitudRrhhService service;

    private MultipartFile archivo() {
        return new MockMultipartFile(
                "sustento", "sustento.pdf", "application/pdf", "contenido".getBytes());
    }

    private TipoSolicitudRrhh tipoComisionDia() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setCodigo("COMISION_DIA");
        t.setNombre("Comisión de servicio por día");
        return t;
    }

    private TipoSolicitudRrhh tipoLicencia() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setCodigo("011");
        t.setNombre("Licencia");
        return t;
    }

    private void stubLicencia(Long tipoLicenciaId, int esSinGoce) {
        TipoLicencia t = new TipoLicencia();
        t.setEsSinGoce(esSinGoce);
        lenient().when(tipoLicenciaRepository.findById(tipoLicenciaId))
                .thenReturn(Optional.of(t));
    }

    @Test
    @DisplayName("Caso feliz: COMISION_DIA con archivo adjunto → no lanza")
    void comisionDiaConArchivoNoLanza() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();

        assertDoesNotThrow(() ->
                service.validarSustentoObligatorio(dto, tipoComisionDia(), archivo()));
    }

    @Test
    @DisplayName("Caso feliz: Licencia con goce con archivo adjunto → no lanza")
    void licenciaConGoceConArchivoNoLanza() {
        stubLicencia(ID_TIPO_LICENCIA_CON_GOCE, 0);

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setTipoLicenciaId(ID_TIPO_LICENCIA_CON_GOCE);

        assertDoesNotThrow(() ->
                service.validarSustentoObligatorio(dto, tipoLicencia(), archivo()));
    }

    @Test
    @DisplayName("Error normativo: COMISION_DIA sin archivo → NegocioException")
    void comisionDiaSinArchivoLanza() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();

        assertThrows(NegocioException.class, () ->
                service.validarSustentoObligatorio(dto, tipoComisionDia(), null));
    }

    @Test
    @DisplayName("Error normativo: Licencia con goce sin archivo → NegocioException")
    void licenciaConGoceSinArchivoLanza() {
        stubLicencia(ID_TIPO_LICENCIA_CON_GOCE, 0);

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setTipoLicenciaId(ID_TIPO_LICENCIA_CON_GOCE);

        assertThrows(NegocioException.class, () ->
                service.validarSustentoObligatorio(dto, tipoLicencia(), null));
    }

    @Test
    @DisplayName("Borde: Licencia SIN GOCE sin archivo → no lanza (usa su propio flujo de firma)")
    void licenciaSinGoceSinArchivoNoLanza() {
        stubLicencia(ID_TIPO_LICENCIA_SIN_GOCE, 1);

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setTipoLicenciaId(ID_TIPO_LICENCIA_SIN_GOCE);

        assertDoesNotThrow(() ->
                service.validarSustentoObligatorio(dto, tipoLicencia(), null));
    }

    @Test
    @DisplayName("Regresión: otro tipo de papeleta sin archivo → el guard no le aplica")
    void otroTipoNoAplicaElGuard() {
        TipoSolicitudRrhh otro = new TipoSolicitudRrhh();
        otro.setCodigo("001");

        SolicitudRrhhDto dto = new SolicitudRrhhDto();

        assertDoesNotThrow(() ->
                service.validarSustentoObligatorio(dto, otro, null));
    }

    @Test
    @DisplayName("Borde: archivo vacío (0 bytes) para COMISION_DIA → NegocioException")
    void comisionDiaConArchivoVacioLanza() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        MultipartFile vacio = new MockMultipartFile("sustento", new byte[0]);

        assertThrows(NegocioException.class, () ->
                service.validarSustentoObligatorio(dto, tipoComisionDia(), vacio));
    }
}
