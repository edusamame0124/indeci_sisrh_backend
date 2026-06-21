package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoPensionDto;
import com.indeci.rrhh.dto.EmpleadoPensionResponseDto;
import com.indeci.rrhh.dto.TasasVigentesPensionDto;
import com.indeci.rrhh.entity.EmpleadoPension;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.entity.TipoComisionAfp;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.RegimenPensionarioRepository;
import com.indeci.rrhh.repository.TipoComisionAfpRepository;

import java.math.BigDecimal;
import java.util.Set;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPensionService {

    private static final Set<String> TIPOS_SIN_APORTE = Set.of(
            "PENSIONISTA",
            "RETIRO",
            "AFP_RETIRO",
            "SIN_REGIMEN",
            "SIN_REGIMEN_PENSIONARIO",
            "SIN_APORTE");

    private final EmpleadoPensionRepository repository;
    private final AuditoriaContext auditoriaContext;
    private final RegimenPensionarioRepository regimenPensionarioRepository;
    private final TipoComisionAfpRepository tipoComisionAfpRepository;
    private final ParametroRemunerativoService parametroService;
    

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_PENSION")
    public void guardar(EmpleadoPensionDto dto) {

     

        // 🔥 VALIDACIÓN EXISTENTE
        Optional<EmpleadoPension> existente =
                repository.findFirstByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        if (existente.isPresent()) {
            auditoriaContext.setDetalle("Ya existe pensión activa para empleado: " + dto.getEmpleadoId());
            throw new NegocioException("Ya existe una pensión activa");
        }

        // 🔹 guardar normal
        EmpleadoPension entity = new EmpleadoPension();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setRegimenPensionarioId(dto.getRegimenPensionarioId());
        entity.setTipoComisionAfpId(dto.getTipoComisionAfpId());
        entity.setTipoRegimen(dto.getTipoRegimen());
        entity.setCuspp(dto.getCuspp());
        entity.setPorcentajeAporte(dto.getPorcentajeAporte());
        entity.setPorcentajeComision(dto.getPorcentajeComision());
        entity.setPorcentajeSeguro(dto.getPorcentajeSeguro());
        entity.setActivo(1);
        entity.setFechaInicio(LocalDate.now());
        entity.setCreatedAt(LocalDateTime.now());

        aplicarCondicionEspecialAfp(entity, dto);

        repository.save(entity);

        auditoriaContext.setDetalle("Pensión creada para empleado: " + dto.getEmpleadoId());
    }
    // ============================
    // LISTAR
    // ============================
    public List<EmpleadoPensionResponseDto> listar(Long empleadoId) {

        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .map(e -> {
                    EmpleadoPensionResponseDto dto = new EmpleadoPensionResponseDto();
                    dto.setId(e.getId());

                    dto.setRegimenPensionarioId(
                            e.getRegimenPensionarioId());

                    if (e.getRegimenPensionarioId() != null) {

                        RegimenPensionario regimen =
                                regimenPensionarioRepository
                                        .findById(
                                                e.getRegimenPensionarioId())
                                        .orElse(null);

                        if (regimen != null) {

                            dto.setRegimenPensionario(
                                    regimen.getNombre());
                        }
                    }

                    if (e.getTipoComisionAfpId() != null) {

                        TipoComisionAfp tipoComision =
                                tipoComisionAfpRepository
                                        .findById(
                                                e.getTipoComisionAfpId())
                                        .orElse(null);

                        if (tipoComision != null) {

                            dto.setTipoComisionAfp(
                                    tipoComision.getNombre());
                        }
                    }

                    dto.setCuspp(
                            e.getCuspp());

                    dto.setPorcentajeAporte(
                            e.getPorcentajeAporte());

                    dto.setPorcentajeComision(
                            e.getPorcentajeComision());

                    dto.setPorcentajeSeguro(
                            e.getPorcentajeSeguro());

                    dto.setTipoComisionAfpId(
                            e.getTipoComisionAfpId());
                    
                    

                    dto.setTipoRegimen(e.getTipoRegimen());
                    dto.setActivo(e.getActivo());
                    dto.setCondicionEspecialAfp(e.getCondicionEspecialAfp());
                    dto.setFechaCondicionAfp(e.getFechaCondicionAfp());
                    dto.setDocumentoSustentoId(e.getDocumentoSustentoId());
                    dto.setObservacionCondicionAfp(e.getObservacionCondicionAfp());
                    return dto;
                }).toList();
    }

    // ============================
    // ACTUALIZAR
    // ============================
    @Auditable(accion = "ACTUALIZAR_PENSION")
    public void actualizar(Long id, EmpleadoPensionDto dto) {

        EmpleadoPension entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Pensión no encontrada"));
        
   

        entity.setRegimenPensionarioId(dto.getRegimenPensionarioId());
        entity.setTipoComisionAfpId(dto.getTipoComisionAfpId());
        entity.setTipoRegimen(dto.getTipoRegimen());
        entity.setCuspp(dto.getCuspp());
        entity.setPorcentajeAporte(dto.getPorcentajeAporte());
        entity.setPorcentajeComision(dto.getPorcentajeComision());
        entity.setPorcentajeSeguro(dto.getPorcentajeSeguro());

        aplicarCondicionEspecialAfp(entity, dto);

        repository.save(entity);

        auditoriaContext.setDetalle("Pensión actualizada ID: " + id);
    }

    // ============================
    // ELIMINAR (LÓGICO)
    // ============================
    @Auditable(accion = "ELIMINAR_PENSION")
    public void eliminar(Long id) {

        EmpleadoPension entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Pensión no encontrada"));

        entity.setActivo(0);
        entity.setFechaFin(LocalDate.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Pensión desactivada ID: " + id);
    }

    // ============================
    // CONDICIÓN ESPECIAL AFP
    // ============================

    private static final Set<String> CONDICIONES_SIN_APORTE_AFP = Set.of(
            "RETIRO_955", "PENSIONISTA_SPP");

    /**
     * Aplica los campos de condición especial AFP a la entidad.
     * Para AFP: persiste la condición (default NO_APLICA).
     * RETIRO_955 y PENSIONISTA_SPP requieren observación de sustento.
     * Para ONP y otros regímenes: limpia los campos (no aplica).
     */
    private void aplicarCondicionEspecialAfp(EmpleadoPension entity, EmpleadoPensionDto dto) {
        if (!"AFP".equalsIgnoreCase(dto.getTipoRegimen())) {
            entity.setCondicionEspecialAfp(null);
            entity.setFechaCondicionAfp(null);
            entity.setDocumentoSustentoId(null);
            entity.setObservacionCondicionAfp(null);
            return;
        }

        String condicion = dto.getCondicionEspecialAfp() != null
                ? dto.getCondicionEspecialAfp().trim().toUpperCase()
                : "NO_APLICA";

        if (CONDICIONES_SIN_APORTE_AFP.contains(condicion)) {
            if (dto.getObservacionCondicionAfp() == null
                    || dto.getObservacionCondicionAfp().isBlank()) {
                throw new NegocioException(
                        "La condición " + condicion + " requiere observación de sustento documental.");
            }
        }

        entity.setCondicionEspecialAfp(condicion);
        entity.setFechaCondicionAfp(dto.getFechaCondicionAfp());
        entity.setDocumentoSustentoId(dto.getDocumentoSustentoId());
        entity.setObservacionCondicionAfp(dto.getObservacionCondicionAfp());
    }

    // ============================
    // Spec 013 / C1 — TASAS VIGENTES (autocomplete del modal)
    // ============================

    /**
     * Devuelve las tasas pensionarias vigentes (aporte / comisión / prima)
     * para el régimen y, si aplica, el tipo de comisión AFP elegido. Solo
     * lectura — no modifica nada.
     *
     * <p>ONP: aporte = TASA_ONP global; comisión y prima quedan en null.
     * <br>AFP: aporte = TASA_AFP_APORTE, prima = PRIMA_AFP. La comisión sale
     * del parámetro {@code COMISION_AFP_<REGIMEN_CODIGO>_<TIPO_COMISION_CODIGO>}
     * sembrado por V010_26; si no está parametrizada, queda en null y
     * {@code comisionParametrizada=false} para que el frontend lo señale.
     */
    public TasasVigentesPensionDto tasasVigentes(
            Long regimenPensionarioId, Long tipoComisionAfpId, Integer anioFiscal) {

        if (regimenPensionarioId == null) {
            throw new NegocioException("regimenPensionarioId es obligatorio");
        }
        int anio = anioFiscal != null ? anioFiscal : LocalDate.now().getYear();

        RegimenPensionario regimen = regimenPensionarioRepository.findById(regimenPensionarioId)
                .orElseThrow(() -> new NegocioException(
                        "Régimen pensionario no existe: id=" + regimenPensionarioId));

        String tipo = regimen.getTipo() != null ? regimen.getTipo().toUpperCase() : "";

        TasasVigentesPensionDto dto = new TasasVigentesPensionDto();
        dto.setTipoRegimen(tipo);

        if ("ONP".equals(tipo)) {
            // ONP: solo aporte. Comisión y prima no aplican.
            dto.setAporte(parametroService.obtenerValorOpcional("TASA_ONP", anio, null)
                    .orElse(null));
            dto.setComision(null);
            dto.setPrima(null);
            dto.setComisionParametrizada(true); // no aplica el concepto
            return dto;
        }

        if (TIPOS_SIN_APORTE.contains(tipo)) {
            dto.setAporte(null);
            dto.setComision(null);
            dto.setPrima(null);
            dto.setComisionParametrizada(true);
            return dto;
        }

        // AFP: aporte y prima son globales; comisión depende de AFP + tipo.
        dto.setAporte(parametroService.obtenerValorOpcional("TASA_AFP_APORTE", anio, null)
                .orElse(null));
        dto.setPrima(parametroService.obtenerValorOpcional("PRIMA_AFP", anio, null)
                .orElse(null));

        if (tipoComisionAfpId == null) {
            // No se eligió tipo todavía — el modal aún no puede autocompletar comisión.
            dto.setComision(null);
            dto.setComisionParametrizada(false);
            return dto;
        }

        TipoComisionAfp tipoComision = tipoComisionAfpRepository.findById(tipoComisionAfpId)
                .orElseThrow(() -> new NegocioException(
                        "Tipo de comisión AFP no existe: id=" + tipoComisionAfpId));

        String codigoParam = "COMISION_AFP_"
                + safeUpper(regimen.getCodigo()) + "_"
                + safeUpper(tipoComision.getCodigo());

        BigDecimal comision = parametroService
                .obtenerValorOpcional(codigoParam, anio, null)
                .orElse(null);

        dto.setComision(comision);
        dto.setComisionParametrizada(comision != null);
        return dto;
    }

    private static String safeUpper(String s) {
        return s == null ? "" : s.toUpperCase();
    }
}
