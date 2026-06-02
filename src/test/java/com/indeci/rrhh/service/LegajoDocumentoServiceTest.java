package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.LegajoDocumentoDto;
import com.indeci.rrhh.dto.LegajoDocumentoResponseDto;
import com.indeci.rrhh.entity.LegajoCategoria;
import com.indeci.rrhh.entity.LegajoDocumento;
import com.indeci.rrhh.entity.LegajoSubcategoria;
import com.indeci.rrhh.repository.LegajoCategoriaRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;
import com.indeci.rrhh.repository.LegajoSubcategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2.6 — Tests del LegajoDocumentoService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegajoDocumentoServiceTest {

    @Mock private LegajoDocumentoRepository repository;
    @Mock private LegajoCategoriaRepository categoriaRepository;
    @Mock private LegajoSubcategoriaRepository subcategoriaRepository;
    @Mock private FtpService ftpService;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private LegajoDocumentoService service;

    private static final Long EMP_ID = 42L;
    private static final Long CAT_SUBSIDIOS = 4L;
    private static final Long SUBCAT_MATERNIDAD = 11L;

    @BeforeEach
    void setUp() {
        LegajoCategoria cat = new LegajoCategoria();
        cat.setId(CAT_SUBSIDIOS);
        cat.setNombre("Subsidios");
        cat.setActivo(1);
        when(categoriaRepository.findById(CAT_SUBSIDIOS))
                .thenReturn(Optional.of(cat));

        LegajoSubcategoria sub = new LegajoSubcategoria();
        sub.setId(SUBCAT_MATERNIDAD);
        sub.setCategoriaId(CAT_SUBSIDIOS);
        sub.setNombre("Maternidad");
        sub.setActivo(1);
        when(subcategoriaRepository.findById(SUBCAT_MATERNIDAD))
                .thenReturn(Optional.of(sub));

        when(ftpService.subirArchivo(any(MultipartFile.class), any(), any()))
                .thenAnswer(inv -> "/SISRRHH/"
                        + inv.getArgument(1) + "/" + inv.getArgument(2));

        when(repository.save(any(LegajoDocumento.class)))
                .thenAnswer(inv -> {
                    LegajoDocumento d = inv.getArgument(0);
                    if (d.getId() == null) d.setId(500L);
                    return d;
                });
    }

    // =================== CASOS FELICES ===================

    @Test
    void subir_feliz_path_FTP_y_persiste_metadata() {
        MultipartFile file = new MockMultipartFile(
                "file", "certificado.pdf", "application/pdf", "datos".getBytes());

        LegajoDocumentoDto dto = base();
        LegajoDocumentoResponseDto r = service.subir(dto, file);

        assertThat(r.getId()).isEqualTo(500L);
        assertThat(r.getCategoriaNombre()).isEqualTo("Subsidios");
        assertThat(r.getExtension()).isEqualTo("pdf");
        assertThat(r.getRutaArchivo()).startsWith("/SISRRHH/empleado-42/subsidios/");
        assertThat(r.getNombreArchivo()).endsWith("_certificado.pdf");
        assertThat(r.getOrigen()).isEqualTo("MANUAL");
        assertThat(r.getVersionDoc()).isEqualTo(1);
        assertThat(r.getPesoArchivo()).isEqualTo(5L);

        verify(ftpService).subirArchivo(eq(file), contains("empleado-42/subsidios"), any());
    }

    @Test
    void subir_con_subcategoria_valida_la_pertenencia() {
        MultipartFile file = new MockMultipartFile(
                "file", "certificado.pdf", "application/pdf", "x".getBytes());

        LegajoDocumentoDto dto = base();
        dto.setSubcategoriaId(SUBCAT_MATERNIDAD);

        LegajoDocumentoResponseDto r = service.subir(dto, file);
        assertThat(r.getSubcategoriaId()).isEqualTo(SUBCAT_MATERNIDAD);
        assertThat(r.getSubcategoriaNombre()).isEqualTo("Maternidad");
    }

    @Test
    void subir_usa_origen_y_referenciaId_si_se_proveen() {
        MultipartFile file = new MockMultipartFile(
                "file", "img.jpg", "image/jpeg", "x".getBytes());

        LegajoDocumentoDto dto = base();
        dto.setOrigen("EVENTO");
        dto.setReferenciaId(777L);

        LegajoDocumentoResponseDto r = service.subir(dto, file);
        assertThat(r.getOrigen()).isEqualTo("EVENTO");
        assertThat(r.getReferenciaId()).isEqualTo(777L);
    }

    @Test
    void subir_usa_nombre_documento_dto_o_fallback_a_archivo_original() {
        MultipartFile file = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "x".getBytes());

        // Caso 1: dto.nombreDocumento provisto.
        LegajoDocumentoDto dto = base();
        dto.setNombreDocumento("Certificado médico mayo 2026");
        assertThat(service.subir(dto, file).getNombreDocumento())
                .isEqualTo("Certificado médico mayo 2026");

        // Caso 2: dto.nombreDocumento null → fallback a sanitizado.
        LegajoDocumentoDto dto2 = base();
        assertThat(service.subir(dto2, file).getNombreDocumento())
                .isEqualTo("cert.pdf");
    }

    @Test
    void eliminar_baja_logica_activo_0() {
        LegajoDocumento e = new LegajoDocumento();
        e.setId(500L);
        e.setActivo(1);
        when(repository.findById(500L)).thenReturn(Optional.of(e));

        service.eliminar(500L);

        assertThat(e.getActivo()).isEqualTo(0);
        verify(repository).save(e);
    }

    // =================== CASOS DE ERROR ===================

    @Test
    void subir_sin_empleadoId_lanza_y_no_toca_FTP() {
        MultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "x".getBytes());

        LegajoDocumentoDto dto = base();
        dto.setEmpleadoId(null);

        assertThatThrownBy(() -> service.subir(dto, file))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("empleadoId");

        verify(ftpService, never()).subirArchivo(any(), any(), any());
    }

    @Test
    void subir_sin_categoriaId_lanza() {
        MultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "x".getBytes());

        LegajoDocumentoDto dto = base();
        dto.setCategoriaId(null);

        assertThatThrownBy(() -> service.subir(dto, file))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("categoriaId");
    }

    @Test
    void subir_categoria_inexistente_lanza() {
        MultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "x".getBytes());

        LegajoDocumentoDto dto = base();
        dto.setCategoriaId(999L);
        when(categoriaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subir(dto, file))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Categoría");
    }

    @Test
    void subir_subcategoria_de_otra_categoria_lanza() {
        // Subcategoría 11 está bajo CAT_SUBSIDIOS, pero dto pide categoría 4 distinta.
        LegajoCategoria otraCat = new LegajoCategoria();
        otraCat.setId(99L);
        otraCat.setNombre("Otros");
        otraCat.setActivo(1);
        when(categoriaRepository.findById(99L)).thenReturn(Optional.of(otraCat));

        MultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", "x".getBytes());

        LegajoDocumentoDto dto = base();
        dto.setCategoriaId(99L);
        dto.setSubcategoriaId(SUBCAT_MATERNIDAD);

        assertThatThrownBy(() -> service.subir(dto, file))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no pertenece a la categoría");
    }

    @Test
    void subir_archivo_vacio_lanza() {
        MultipartFile file = new MockMultipartFile(
                "file", "vacio.pdf", "application/pdf", new byte[0]);

        LegajoDocumentoDto dto = base();

        assertThatThrownBy(() -> service.subir(dto, file))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("vacío");

        verify(ftpService, never()).subirArchivo(any(), any(), any());
    }

    // =================== HELPERS ESTÁTICOS ===================

    @Test
    void sanitizar_reemplaza_caracteres_inseguros() {
        assertThat(LegajoDocumentoService.sanitizar("a b/c?.pdf")).isEqualTo("a_b_c_.pdf");
        assertThat(LegajoDocumentoService.sanitizar("normal-doc_1.pdf")).isEqualTo("normal-doc_1.pdf");
        assertThat(LegajoDocumentoService.sanitizar(null)).isEqualTo("archivo");
        assertThat(LegajoDocumentoService.sanitizar("   ")).isEqualTo("___");
    }

    @Test
    void slug_normaliza_nombre_categoria() {
        assertThat(LegajoDocumentoService.slug("Subsidios")).isEqualTo("subsidios");
        assertThat(LegajoDocumentoService.slug("Permisos y licencias"))
                .isEqualTo("permisos-y-licencias");
        assertThat(LegajoDocumentoService.slug("Año fiscal"))
                .isEqualTo("ano-fiscal");
        assertThat(LegajoDocumentoService.slug(null)).isEqualTo("otros");
    }

    @Test
    void extensionDe_extrae_correcto_minusculas() {
        assertThat(LegajoDocumentoService.extensionDe("cert.PDF")).isEqualTo("pdf");
        assertThat(LegajoDocumentoService.extensionDe("foto.jpeg")).isEqualTo("jpeg");
        assertThat(LegajoDocumentoService.extensionDe("sin_extension")).isNull();
        assertThat(LegajoDocumentoService.extensionDe("termina_punto.")).isNull();
        // > 10 caracteres no es extensión real.
        assertThat(LegajoDocumentoService.extensionDe("a.muylargaextension")).isNull();
    }

    @Test
    void listar_devuelve_documentos_del_empleado_con_denormalizacion() {
        LegajoDocumento d1 = doc(1L, CAT_SUBSIDIOS, SUBCAT_MATERNIDAD);
        LegajoDocumento d2 = doc(2L, CAT_SUBSIDIOS, null);

        when(repository.findByEmpleadoIdAndActivoOrderByCreatedAtDesc(EMP_ID, 1))
                .thenReturn(java.util.List.of(d1, d2));

        var r = service.listarPorEmpleado(EMP_ID);
        assertThat(r).hasSize(2);
        assertThat(r.get(0).getCategoriaNombre()).isEqualTo("Subsidios");
        assertThat(r.get(0).getSubcategoriaNombre()).isEqualTo("Maternidad");
        assertThat(r.get(1).getSubcategoriaNombre()).isNull();
    }

    // =================== HELPERS ===================

    private LegajoDocumentoDto base() {
        LegajoDocumentoDto dto = new LegajoDocumentoDto();
        dto.setEmpleadoId(EMP_ID);
        dto.setCategoriaId(CAT_SUBSIDIOS);
        return dto;
    }

    private LegajoDocumento doc(Long id, Long catId, Long subId) {
        LegajoDocumento d = new LegajoDocumento();
        d.setId(id);
        d.setEmpleadoId(EMP_ID);
        d.setCategoriaId(catId);
        d.setSubcategoriaId(subId);
        d.setActivo(1);
        return d;
    }
}
