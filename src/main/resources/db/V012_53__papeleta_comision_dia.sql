-- ============================================================================
-- V012_53 — Papeleta "Comisión de servicio por día" (código 'COMISION_DIA')
--
-- Nuevo TIPO de papeleta sobre INDECI_SOLICITUD_RRHH, independiente del '006'
-- (Comisión de servicio por horas, renombrado en V012_52). No modifica ninguna
-- fila existente del catálogo ni el comportamiento de los otros 12+ tipos.
--
-- Reutiliza columnas genéricas ya existentes en INDECI_SOLICITUD_RRHH
-- (FECHA_INICIO/FECHA_FIN/LUGAR_COMISION/ARCHIVO_SUSTENTO/CANTIDAD_DIAS) —
-- sin cambios de esquema en esa tabla.
--
-- Flags:
--   MOSTRAR_HORAS=0          -> formulario de rango de fechas (sin hora salida/ingreso)
--   REQUIERE_LUGAR=1         -> exige "Lugar de comisión" (igual que '006')
--   REQUIERE_SUSTENTO=0      -> el documento de sustento es opcional (RR.HH., 2026-08-04);
--                                el diálogo lo muestra siempre, sin bloquear el guardado
--   REQUIERE_OBSERVACION=0
--   JUSTIFICA_ASISTENCIA=1   -> una papeleta APROBADA exime la FALTA del día completo
--                                (RR.HH., 2026-08-04: cubre el día entero fuera de oficina)
--
-- Idempotente. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM INDECI_TIPO_SOLICITUD_RRHH
     WHERE CODIGO = 'COMISION_DIA';

    IF v_exists = 0 THEN
        INSERT INTO INDECI_TIPO_SOLICITUD_RRHH (
            NOMBRE, CODIGO, ACTIVO,
            MOSTRAR_HORAS, REQUIERE_SUSTENTO, REQUIERE_LUGAR,
            REQUIERE_OBSERVACION, MOSTRAR_LACTANCIA, MOSTRAR_DESCANSO_MEDICO,
            MOSTRAR_LICENCIA, MOSTRAR_VACACION, MOSTRAR_COMPENSACION,
            JUSTIFICA_ASISTENCIA
        ) VALUES (
            'Comisión de servicio por día', 'COMISION_DIA', 1,
            0, 0, 1,
            0, 0, 0,
            0, 0, 0,
            1
        );
        DBMS_OUTPUT.PUT_LINE('Tipo COMISION_DIA -> creado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Tipo COMISION_DIA ya existe. Sin cambios.');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_53 finalizado correctamente.');
END;
/
