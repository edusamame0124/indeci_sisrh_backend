package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDocDto;
import com.indeci.rrhh.dto.SolicitudRrhhDocResponseDto;
import com.indeci.rrhh.entity.SolicitudRrhhDoc;
import com.indeci.rrhh.repository.SolicitudRrhhDocRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SolicitudRrhhDocService {

    private final SolicitudRrhhDocRepository
            repository;

    private final SolicitudRrhhRepository
            solicitudRepository;

    // ==========================================
    // SUBIR DOCUMENTO
    // ==========================================

    public void registrar(
            SolicitudRrhhDocDto dto) {

        solicitudRepository
                .findById(dto.getSolicitudId())
                .orElseThrow(() ->
                        new NegocioException(
                                "Solicitud no encontrada"));

        SolicitudRrhhDoc entity =
                new SolicitudRrhhDoc();

        entity.setSolicitudId(
                dto.getSolicitudId());

        entity.setEtapa(
                dto.getEtapa());

        entity.setNombreArchivo(
                dto.getNombreArchivo());

        entity.setRutaArchivo(
                dto.getRutaArchivo());

        entity.setVersionDoc(
                dto.getVersionDoc());

        entity.setObservacion(
                dto.getObservacion());

        entity.setUsuarioUpload(
                "ADMIN");

        entity.setCreatedAt(
                LocalDateTime.now());

        entity.setActivo(1);

        repository.save(entity);
    }

    // ==========================================
    // LISTAR DOCUMENTOS
    // ==========================================

    public List<SolicitudRrhhDocResponseDto>
    listar(Long solicitudId) {

        return repository
                .findBySolicitudIdAndActivoOrderByVersionDocAsc(
                        solicitudId,
                        1)
                .stream()
                .map(this::convertir)
                .toList();
    }

    private SolicitudRrhhDocResponseDto
    convertir(SolicitudRrhhDoc d) {

        SolicitudRrhhDocResponseDto dto =
                new SolicitudRrhhDocResponseDto();

        dto.setId(d.getId());

        dto.setSolicitudId(
                d.getSolicitudId());

        dto.setEtapa(
                d.getEtapa());

        dto.setNombreArchivo(
                d.getNombreArchivo());

        dto.setRutaArchivo(
                d.getRutaArchivo());

        dto.setVersionDoc(
                d.getVersionDoc());

        dto.setObservacion(
                d.getObservacion());

        dto.setUsuarioUpload(
                d.getUsuarioUpload());

        dto.setCreatedAt(
                d.getCreatedAt());

        return dto;
    }
}