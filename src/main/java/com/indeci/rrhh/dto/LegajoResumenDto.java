package com.indeci.rrhh.dto;

import java.util.List;

import lombok.Data;

@Data
public class LegajoResumenDto {

    private PersonaEmpleadoResponseDto persona;

    private byte[] fotoPerfil;

    private List<FormacionAcademicaResponseDto>
            formacionAcademica;

    private List<CapacitacionResponseDto>
            capacitaciones;

    private List<IdiomaResponseDto>
            idiomas;

    private List<ConocimientoInformaticoResponseDto>
            conocimientosInformaticos;

    private List<FamiliarResponseDto>
            familiares;

    private List<ExperienciaLaboralResponseDto>
            experienciaLaboralExterna;

    private List<ReconocimientoResponseDto>
            reconocimientos;

    private List<MedidaDisciplinariaResponseDto>
            medidasDisciplinarias;
}