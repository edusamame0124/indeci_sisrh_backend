package com.indeci.rrhh.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.entity.CorrelativoDocumento;
import com.indeci.rrhh.repository.CorrelativoDocumentoRepository;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M14 — Emisión de correlativos de documento (NRO_PLANILLA MCPP).
 *
 * <p>Devuelve el siguiente número para una combinación (entidad, año, mes, tipo)
 * de forma atómica: lee la fila con lock pesimista, incrementa y persiste. PLAME
 * no usa este servicio (su nombre de archivo es determinístico).
 */
@Service
@RequiredArgsConstructor
public class CorrelativoService {

    private final CorrelativoDocumentoRepository repository;

    /**
     * Siguiente correlativo para la combinación dada. Debe ejecutarse en
     * transacción (lo está por @Transactional); el lock pesimista serializa las
     * llamadas concurrentes sobre una fila existente.
     *
     * <p>Primera vez para una combinación: crea la fila en 0 y devuelve 1. La
     * carrera de creación simultánea la corta la UNIQUE (entidad, año, mes, tipo).
     */
    @Transactional
    public long siguiente(String codEntidad, int anio, int mes, String tipoDocumento) {
        CorrelativoDocumento fila = repository
                .findByCodEntidadAndAnioAndMesAndTipoDocumento(codEntidad, anio, mes, tipoDocumento)
                .orElseGet(() -> crearInicial(codEntidad, anio, mes, tipoDocumento));

        long siguiente = fila.getUltimoNro() + 1;
        fila.setUltimoNro(siguiente);
        repository.save(fila);
        return siguiente;
    }

    private CorrelativoDocumento crearInicial(
            String codEntidad, int anio, int mes, String tipoDocumento) {
        CorrelativoDocumento nuevo = new CorrelativoDocumento();
        nuevo.setCodEntidad(codEntidad);
        nuevo.setAnio(anio);
        nuevo.setMes(mes);
        nuevo.setTipoDocumento(tipoDocumento);
        nuevo.setUltimoNro(0L);
        return repository.save(nuevo);
    }
}
