package com.indeci.rrhh.service.subsidio;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.entity.SubsidioTimelineEvento;
import com.indeci.rrhh.repository.SubsidioTimelineEventoRepository;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioTimelineService {

    private final SubsidioTimelineEventoRepository repository;

    @Transactional
    public void registrar(Long casoId, String tipo, String descripcion, Long referenciaId) {
        SubsidioTimelineEvento evt = new SubsidioTimelineEvento();
        evt.setCasoId(casoId);
        evt.setTipoEvento(tipo);
        evt.setDescripcion(descripcion);
        if (referenciaId != null) {
            evt.setDetalleJson("{\"refId\":" + referenciaId + "}");
        }
        evt.setUsuario(SecurityUtil.getUsername());
        evt.setCreatedAt(LocalDateTime.now());
        repository.save(evt);
    }
}
