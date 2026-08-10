package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;

/**
 * Permiso de Justificación de Omisión de Registro de Asistencia (código '004') — guard de
 * {@code validarHoras}: es una sola marca faltante (entrada XOR salida), no un rango, así que
 * debe exigir EXACTAMENTE una de las dos horas, nunca ambas ni ninguna. Regresión del bug donde
 * el guard heredado de los permisos "por horas" exigía ambas y bloqueaba cualquier omisión
 * (ver mapeo Ingreso→horaInicio / Salida→horaFin en permiso-comun-dialog.ts). Cubre caso feliz
 * (ambas direcciones), error normativo (ambas horas) y borde (ninguna hora).
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhOmisionHorasValidacionTest {

    @InjectMocks
    private SolicitudRrhhService service;

    private TipoSolicitudRrhh tipoOmision() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setCodigo("004");
        t.setNombre("Permiso de Justificación de Omisión de Registro de Asistencia");
        t.setMostrarHoras(1);
        return t;
    }

    private SolicitudRrhhDto dto(String horaInicio, String horaFin) {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setHoraInicio(horaInicio);
        dto.setHoraFin(horaFin);
        return dto;
    }

    @Test
    @DisplayName("Caso feliz: solo horaInicio (Ingreso) → no lanza")
    void soloHoraInicio_noLanza() {
        assertDoesNotThrow(() -> service.validarHoras(dto("08:30", null), tipoOmision()));
    }

    @Test
    @DisplayName("Caso feliz (simetría): solo horaFin (Salida) → no lanza")
    void soloHoraFin_noLanza() {
        assertDoesNotThrow(() -> service.validarHoras(dto(null, "17:00"), tipoOmision()));
    }

    @Test
    @DisplayName("Error normativo: ambas horas presentes → NegocioException (no es un rango)")
    void ambasHoras_rechaza() {
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarHoras(dto("08:30", "17:00"), tipoOmision()));

        assertEquals(
                "Debe indicar exactamente una hora: de ingreso o de salida "
                        + "(Omisión de Registro es una marca faltante, no un rango).",
                ex.getMessage());
    }

    @Test
    @DisplayName("Borde: ninguna hora presente → NegocioException")
    void ningunaHora_rechaza() {
        assertThrows(NegocioException.class,
                () -> service.validarHoras(dto(null, null), tipoOmision()));
    }

    @Test
    @DisplayName("Regresión: tipo NO-omisión sigue exigiendo AMBAS horas como antes")
    void tipoNoOmision_exigeAmbasHoras() {
        TipoSolicitudRrhh otro = new TipoSolicitudRrhh();
        otro.setCodigo("001");
        otro.setMostrarHoras(1);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarHoras(dto(null, "17:00"), otro));

        assertEquals("Hora inicio es obligatoria", ex.getMessage());
    }
}
