package com.indeci.rrhh.report.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.indeci.rrhh.dto.LegajoResumenDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.service.LegajoResumenService;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;

@Service
@RequiredArgsConstructor
public class LegajoReportService {

    private final LegajoResumenService
            legajoResumenService;
    
    
    public String generarPdf(
            Long personaId) {

        try {

            LegajoResumenDto resumen =
                    legajoResumenService.obtener(
                            personaId);

            PersonaEmpleadoResponseDto persona =
                    resumen.getPersona();
            
            
            
            

            Map<String,Object> params =
                    new HashMap<>();
            
            
            InputStream logo =
                    getClass()
                            .getResourceAsStream(
                                    "/reportes/img/logoPeru.png");

            params.put(
                    "P_LOGO_PERU",
                    logo);
            
            InputStream foto = null;

            if(resumen.getFotoPerfil() != null) {

                foto =
                        new ByteArrayInputStream(
                                resumen.getFotoPerfil());
            }

            params.put(
                    "P_FOTO",
                    foto);
            
            
            params.put(
                    "P_NOMBRE_COMPLETO",
                    persona.getNombreCompleto());

            params.put(
                    "P_TIPO_DOCUMENTO",
                    persona.getTipoDocumento());

            params.put(
                    "P_DNI",
                    persona.getDni());

            params.put(
                    "P_EMAIL",
                    persona.getEmail());

            params.put(
                    "P_TELEFONO",
                    persona.getTelefono());

            params.put(
                    "P_TELEFONO_FIJO",
                    "");

            params.put(
                    "P_DISTRITO",
                    persona.getDistritoId());

            params.put(
                    "P_DIRECCION",
                    persona.getDireccion());

            params.put(
                    "P_NACIONALIDAD",
                    persona.getNacionalidad());

            params.put(
                    "P_RUC",
                    persona.getRuc());

            params.put(
                    "P_CORREO_INSTITUCIONAL",
                    persona.getCorreoInstitucional());

            params.put(
                    "P_SEXO",
                    persona.getSexo());

            params.put(
                    "P_ESTADO_CIVIL",
                    persona.getEstadoCivil());

            params.put(
                    "P_PROFESION",
                    persona.getProfesion());

            params.put(
                    "P_GRADO_ACADEMICO",
                    persona.getGradoAcademico());

            params.put(
                    "P_EMERG_NOMBRE",
                    persona.getContactoEmergenciaNombre());

            params.put(
                    "P_EMERG_PARENTESCO",
                    persona.getContactoEmergenciaParentesco());

            params.put(
                    "P_EMERG_TELEFONO",
                    persona.getContactoEmergenciaTelefono());
            
            
            params.put(
                    "DS_FORMACION",
                    new JRBeanCollectionDataSource(
                            resumen.getFormacionAcademica()));

            params.put(
                    "DS_CAPACITACIONES",
                    new JRBeanCollectionDataSource(
                            resumen.getCapacitaciones()));

            params.put(
                    "DS_CONOCIMIENTOS",
                    new JRBeanCollectionDataSource(
                            resumen.getConocimientosInformaticos()));

            params.put(
                    "DS_FAMILIARES",
                    new JRBeanCollectionDataSource(
                            resumen.getFamiliares()));

            params.put(
                    "DS_EXPERIENCIA",
                    new JRBeanCollectionDataSource(
                            resumen.getExperienciaLaboralExterna()));

            params.put(
                    "DS_RECONOCIMIENTOS",
                    new JRBeanCollectionDataSource(
                            resumen.getReconocimientos()));

            params.put(
                    "DS_MEDIDAS",
                    new JRBeanCollectionDataSource(
                            resumen.getMedidasDisciplinarias()));
            
            params.put(
                    "P_FORMACION_COUNT",
                    resumen.getFormacionAcademica().size());

            params.put(
                    "P_CAPACITACIONES_COUNT",
                    resumen.getCapacitaciones().size());

            params.put(
                    "P_CONOCIMIENTOS_COUNT",
                    resumen.getConocimientosInformaticos().size());

            params.put(
                    "P_FAMILIARES_COUNT",
                    resumen.getFamiliares().size());

            params.put(
                    "P_EXPERIENCIA_COUNT",
                    resumen.getExperienciaLaboralExterna().size());

            params.put(
                    "P_RECONOCIMIENTOS_COUNT",
                    resumen.getReconocimientos().size());

            params.put(
                    "P_MEDIDAS_COUNT",
                    resumen.getMedidasDisciplinarias().size());
         
            JasperReport report =
                    (JasperReport)
                            JRLoader.loadObject(
                                    getClass()
                                            .getResourceAsStream(
                                                    "/reportes/rrhh/legajo_personal.jasper"));

            JasperPrint print =
                    JasperFillManager.fillReport(
                            report,
                            params,
                            new JREmptyDataSource());

            String rutaPdf =
                    System.getProperty("java.io.tmpdir")
                    + "/legajo_"
                    + personaId
                    + ".pdf";

            JasperExportManager.exportReportToPdfFile(
                    print,
                    rutaPdf);

            return rutaPdf;

        } catch (Exception ex) {

            throw new RuntimeException(
                    ex.getMessage(),
                    ex);
        }
    }

}