package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhHistResponseDto;
import com.indeci.rrhh.entity.EstadoSolicitud;
import com.indeci.rrhh.entity.SolicitudRrhhHist;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudRrhhHistRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SolicitudRrhhHistService {

    private final SolicitudRrhhHistRepository
            repository;

    private final EstadoSolicitudRepository
            estadoRepository;

    public List<SolicitudRrhhHistResponseDto>
    listar(Long solicitudId) {

        return repository
                .findBySolicitudIdOrderByFechaAsc(
                        solicitudId)
                .stream()
                .map(this::convertir)
                .toList();
    }

    private SolicitudRrhhHistResponseDto
    convertir(SolicitudRrhhHist h) {

        SolicitudRrhhHistResponseDto dto =
                new SolicitudRrhhHistResponseDto();

        dto.setId(h.getId());

        dto.setAccion(h.getAccion());

        dto.setObservacion(
                h.getObservacion());

        dto.setUsuario(
                h.getUsuario());

        dto.setFecha(
                h.getFecha());

        if (h.getEstadoOrigenId() != null) {

            EstadoSolicitud origen =
                    estadoRepository
                            .findById(
                                    h.getEstadoOrigenId())
                            .orElse(null);

            if (origen != null) {

                dto.setEstadoOrigen(
                        origen.getNombre());
            }
        }

        EstadoSolicitud destino =
                estadoRepository
                        .findById(
                                h.getEstadoDestinoId())
                        .orElse(null);

        if (destino != null) {

            dto.setEstadoDestino(
                    destino.getNombre());
        }

        return dto;
    }
}