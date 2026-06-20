package com.indeci.rrhh.service.subsidio;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.repository.SubsidioReglaVigenciaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioReglaResolverService {

    private final SubsidioReglaVigenciaRepository reglaVigenciaRepository;

    @Transactional(readOnly = true)
    public SubsidioReglaVigencia resolverVigente(LocalDate fecha) {
        return reglaVigenciaRepository.findVigenteEnFecha(fecha)
                .orElseThrow(() -> new NegocioException(
                        "No hay versión vigente aprobada de reglas de subsidio para la fecha "
                                + fecha + " (SUB_V009)."));
    }
}
