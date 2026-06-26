package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.TipoPersonaMefDto;
import com.indeci.rrhh.entity.TipoPersonaMef;
import com.indeci.rrhh.repository.TipoPersonaMefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TipoPersonaMefService {

    private final TipoPersonaMefRepository repository;

    public List<TipoPersonaMefDto> listarActivos() {
        return repository.findByActivoOrderByCodigoAsc(1)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private TipoPersonaMefDto mapToDto(TipoPersonaMef entity) {
        TipoPersonaMefDto dto = new TipoPersonaMefDto();
        dto.setId(entity.getId());
        dto.setCodigo(entity.getCodigo());
        dto.setNombre(entity.getNombre());
        dto.setDescripcion(entity.getDescripcion());
        return dto;
    }
}
