package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.GradoAcademicoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.ProfesionRepository;
import com.indeci.rrhh.repository.SexoRepository;
import com.indeci.rrhh.repository.TipoDocumentoRepository;
import com.indeci.rrhh.repository.TipoPersonalRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonaServiceTest {

    @Mock
    private PersonaRepository personaRepository;
    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private AuditoriaContext auditoriaContext;
    @Mock
    private SexoRepository sexoRepository;
    @Mock
    private ProfesionRepository profesionRepository;
    @Mock
    private GradoAcademicoRepository gradoAcademicoRepository;
    @Mock
    private EstadoCivilRepository estadoCivilRepository;
    @Mock
    private TipoDocumentoRepository tipoDocumentoRepository;
    @Mock
    private TipoPersonalRepository tipoPersonalRepository;
    @Mock
    private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock
    private RegimenLaboralRepository regimenLaboralRepository;

    @InjectMocks
    private PersonaService personaService;

    //@Test
    void listar_includesEmpleadoIdWhenEmpleadoLinked() {
        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("ANA PRUEBA");
        persona.setDni("12345678");

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setPersonaId(1L);
        empleado.setCodigoInterno("EMP00042");
        empleado.setEstado("ACTIVO");

        when(personaRepository.findAll()).thenReturn(List.of(persona));
        when(empleadoRepository.findByPersonaId(1L)).thenReturn(Optional.of(empleado));

        var list = personaService.listar();
        assertEquals(1, list.size());
        assertEquals(42L, list.get(0).getEmpleadoId());
    }

    @Test
    void obtenerPorId_includesEmpleadoIdWhenEmpleadoLinked() {
        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("ANA PRUEBA");
        persona.setDni("12345678");

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setPersonaId(1L);
        empleado.setCodigoInterno("EMP00042");
        empleado.setEstado("ACTIVO");

        when(personaRepository.findById(1L)).thenReturn(Optional.of(persona));
        when(empleadoRepository.findByPersonaId(1L)).thenReturn(Optional.of(empleado));

        var dto = personaService.obtenerPorId(1L);
        assertEquals(42L, dto.getEmpleadoId());
    }

   // @Test
    void listar_empleadoIdNullWhenNoEmpleadoRow() {
        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("SIN EMPLEADO");
        persona.setDni("87654321");

        when(personaRepository.findAll()).thenReturn(List.of(persona));
        when(empleadoRepository.findByPersonaId(1L)).thenReturn(Optional.empty());

        var list = personaService.listar();
        assertEquals(1, list.size());
        assertNull(list.get(0).getEmpleadoId());
    }
}
