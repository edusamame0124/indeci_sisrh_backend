package com.indeci.rrhh.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoRemuneracionHistDto;
import com.indeci.rrhh.dto.RemuneracionCambioInput;
import com.indeci.rrhh.entity.EmpleadoRemuneracionHist;
import com.indeci.rrhh.repository.EmpleadoRemuneracionHistRepository;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

/**
 * Historial remunerativo del vínculo (F2). No sobrescribe: cada cambio
 * (renovación/adenda/incremento) crea una fila nueva y cierra la vigencia
 * anterior. El motor resuelve la base vigente por período desde aquí.
 */
@Service
@RequiredArgsConstructor
public class RemuneracionHistService {

    private final EmpleadoRemuneracionHistRepository repository;

    @Transactional(readOnly = true)
    public List<EmpleadoRemuneracionHistDto> listar(Long empleadoPlanillaId) {
        return repository.findByEmpleadoPlanillaIdOrderByVigenciaDesdeDesc(empleadoPlanillaId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public EmpleadoRemuneracionHistDto registrarCambio(
            Long empleadoPlanillaId, RemuneracionCambioInput input) {
        if (input.vigenciaDesde() == null || input.remuneracionTotal() == null) {
            throw new NegocioException("Vigencia desde y remuneración total son obligatorias");
        }

        // Cerrar la vigencia abierta anterior (si su inicio es previo al nuevo).
        repository.findByEmpleadoPlanillaIdOrderByVigenciaDesdeDesc(empleadoPlanillaId).stream()
                .filter(h -> "APROBADO".equals(h.getEstado())
                        && h.getVigenciaHasta() == null
                        && h.getVigenciaDesde() != null
                        && h.getVigenciaDesde().isBefore(input.vigenciaDesde()))
                .forEach(h -> {
                    h.setVigenciaHasta(input.vigenciaDesde().minusDays(1));
                    repository.save(h);
                });

        EmpleadoRemuneracionHist nuevo = new EmpleadoRemuneracionHist();
        nuevo.setEmpleadoPlanillaId(empleadoPlanillaId);
        nuevo.setVigenciaDesde(input.vigenciaDesde());
        nuevo.setMontoBase(input.montoBase());
        nuevo.setRemuneracionTotal(input.remuneracionTotal());
        nuevo.setTipoCambio(input.tipoCambio() != null ? input.tipoCambio() : "AJUSTE");
        nuevo.setDocumentoSustento(input.documentoSustento());
        nuevo.setFuente("MANUAL");
        nuevo.setEstado("APROBADO");
        nuevo.setObservacion(input.observacion());
        nuevo.setCreatedBy(SecurityUtil.getUsername());
        nuevo.setCreatedAt(LocalDateTime.now());
        return toDto(repository.save(nuevo));
    }

    /**
     * Elimina un cambio remunerativo. Para no dejar huecos de vigencia, el
     * predecesor inmediato se extiende para cubrir el período del eliminado (si el
     * eliminado era el vigente, el predecesor vuelve a quedar abierto/vigente).
     */
    @Transactional
    public void eliminar(Long empleadoPlanillaId, Long historialId) {
        EmpleadoRemuneracionHist h = repository.findById(historialId)
                .orElseThrow(() -> new NegocioException("Cambio remunerativo no encontrado"));
        if (!java.util.Objects.equals(h.getEmpleadoPlanillaId(), empleadoPlanillaId)) {
            throw new NegocioException("El cambio remunerativo no pertenece a este vínculo");
        }
        // Predecesor inmediato (lista DESC → el primero con vigenciaDesde anterior).
        repository.findByEmpleadoPlanillaIdOrderByVigenciaDesdeDesc(empleadoPlanillaId).stream()
                .filter(p -> !p.getId().equals(historialId)
                        && p.getVigenciaDesde() != null && h.getVigenciaDesde() != null
                        && p.getVigenciaDesde().isBefore(h.getVigenciaDesde()))
                .findFirst()
                .ifPresent(p -> {
                    p.setVigenciaHasta(h.getVigenciaHasta());
                    repository.save(p);
                });
        repository.delete(h);
    }

    private EmpleadoRemuneracionHistDto toDto(EmpleadoRemuneracionHist h) {
        return new EmpleadoRemuneracionHistDto(
                h.getId(), h.getVigenciaDesde(), h.getVigenciaHasta(), h.getMontoBase(),
                h.getRemuneracionTotal(), h.getTipoCambio(), h.getDocumentoSustento(),
                h.getFuente(), h.getEstado(), h.getObservacion(), h.getCreatedBy(),
                h.getCreatedAt());
    }
}
