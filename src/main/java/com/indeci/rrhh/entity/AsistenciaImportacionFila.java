package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_ASISTENCIA_IMPORTACION_FILA", schema = "GESTIONRRHH")
@Data
public class AsistenciaImportacionFila {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "IMPORTACION_ID", nullable = false)
    private Long importacionId;

    @Column(name = "NUMERO_FILA", nullable = false)
    private Integer numeroFila;

    @Column(name = "LINEA_ORIGINAL", length = 4000)
    private String lineaOriginal;

    @Column(name = "DNI", length = 8)
    private String dni;

    @Column(name = "FECHA")
    private LocalDate fecha;

    @Column(name = "NOMBRE_CSV", length = 150)
    private String nombreCsv;

    @Column(name = "NOMBRE_SISTEMA", length = 150)
    private String nombreSistema;

    @Column(name = "DIA_SEMANA", length = 12)
    private String diaSemana;

    @Column(name = "ENTRADA_PROG", length = 8)
    private String entradaProg;

    @Column(name = "SALIDA_PROG", length = 8)
    private String salidaProg;

    @Column(name = "MARCA1", length = 16)
    private String marca1;

    @Column(name = "MARCA2", length = 16)
    private String marca2;

    @Column(name = "MARCA3", length = 16)
    private String marca3;

    @Column(name = "MARCA4", length = 16)
    private String marca4;

    @Column(name = "TARDANZA_RAW", length = 16)
    private String tardanzaRaw;

    @Column(name = "TARDANZA_MIN")
    private Integer tardanzaMin = 0;

    @Column(name = "REFRIGERIO_MIN")
    private Integer refrigerioMin = 0;

    @Column(name = "EXCESO_REFRIG_MIN")
    private Integer excesoRefrigMin = 0;

    @Column(name = "TIEMPO_REFRIG_MIN")
    private Integer tiempoRefrigMin = 0;

    @Column(name = "TIEMPO_ANTES_SAL_MIN")
    private Integer tiempoAntesSalMin = 0;

    @Column(name = "HORAS_TRAB_MIN")
    private Integer horasTrabMin = 0;

    @Column(name = "HORAS_EXTRA_25_MIN")
    private Integer horasExtra25Min = 0;

    @Column(name = "HORAS_EXTRA_35_MIN")
    private Integer horasExtra35Min = 0;

    @Column(name = "HORAS_EXTRA_100_MIN")
    private Integer horasExtra100Min = 0;

    @Column(name = "HORAS_EXTRA_TOTAL_MIN")
    private Integer horasExtraTotalMin = 0;

    @Column(name = "OBSERVACION_MARCADOR", length = 500)
    private String observacionMarcador;

    @Column(name = "MENSAJE_VALIDACION", length = 500)
    private String mensajeValidacion;

    @Column(name = "ESTADO_FILA", nullable = false)
    private String estadoFila;

    @Column(name = "ACEPTADA_OBSERVADA")
    private Integer aceptadaObservada = 0;

    @Column(name = "USUARIO_ACEPTA_OBS", length = 100)
    private String usuarioAceptaObs;

    @Column(name = "FECHA_ACEPTA_OBS")
    private LocalDateTime fechaAceptaObs;

    @Column(name = "MOTIVO_ACEPTA_OBS", length = 500)
    private String motivoAceptaObs;

    @Column(name = "ERRORES_JSON", length = 4000)
    private String erroresJson;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "HASH_ARCHIVO", length = 64)
    private String hashArchivo;

    @Column(name = "USUARIO_IMPORTACION")
    private String usuarioImportacion;

    @Column(name = "FECHA_IMPORTACION")
    private LocalDateTime fechaImportacion;
}
