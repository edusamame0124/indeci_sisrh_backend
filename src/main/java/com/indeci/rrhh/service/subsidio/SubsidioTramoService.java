package com.indeci.rrhh.service.subsidio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.repository.SubsidioTramoRepository;
import com.indeci.rrhh.subsidio.SubsidioEstados;
import com.indeci.rrhh.subsidio.SubsidioPeriodoUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioTramoService {

    private final SubsidioCasoRepository casoRepository;
    private final SubsidioTramoRepository tramoRepository;
    private final SubsidioValidacionService validacionService;

    @Transactional
    public List<SubsidioTramo> generarTramos(Long casoId) {
        SubsidioCaso caso = casoRepository.findByIdAndActivo(casoId, 1)
                .orElseThrow(() -> new NegocioException("Caso de subsidio no encontrado"));

        LocalDate inicio = caso.getFechaInicio();
        LocalDate fin = caso.getFechaFin();
        if (fin.isBefore(inicio)) {
            throw new NegocioException("Fechas del caso incoherentes");
        }

        List<SubsidioTramo> generados = new ArrayList<>();
        YearMonth cursor = YearMonth.from(inicio);
        YearMonth finYm = YearMonth.from(fin);

        while (!cursor.isAfter(finYm)) {
            LocalDate mesInicio = cursor.atDay(1);
            LocalDate mesFin = cursor.atEndOfMonth();
            LocalDate tramoDesde = inicio.isAfter(mesInicio) ? inicio : mesInicio;
            LocalDate tramoHasta = fin.isBefore(mesFin) ? fin : mesFin;

            int diasSubsidio = (int) ChronoUnit.DAYS.between(tramoDesde, tramoHasta) + 1;
            int diasMes = cursor.lengthOfMonth();
            int diasLaborados = Math.max(0, diasMes - diasSubsidio);

            String periodo = SubsidioPeriodoUtil.deFecha(tramoDesde);
            tramoRepository.desactivarVigentesPorCasoYPeriodo(casoId, periodo);

            SubsidioTramo tramo = new SubsidioTramo();
            tramo.setCasoId(casoId);
            tramo.setPeriodo(periodo);
            tramo.setFechaDesde(tramoDesde);
            tramo.setFechaHasta(tramoHasta);
            tramo.setDiasSubsidio(diasSubsidio);
            tramo.setDiasLaborados(diasLaborados);
            tramo.setEstadoTramo(SubsidioEstados.TRAMO_BORRADOR);
            tramo.setVersionTramo(1);
            tramo.setEsVigente("S");
            tramo.setActivo(1);
            tramo.setCreatedAt(LocalDateTime.now());
            generados.add(tramoRepository.save(tramo));

            cursor = cursor.plusMonths(1);
        }

        generados.sort(Comparator.comparing(SubsidioTramo::getFechaDesde));
        validacionService.validarTramosSolapados(casoId, generados);
        return generados;
    }

    @Transactional
    public SubsidioTramo actualizar(Long tramoId, Integer diasSubsidio, Integer diasLaborados) {
        SubsidioTramo tramo = tramoRepository.findByIdAndActivo(tramoId, 1)
                .orElseThrow(() -> new NegocioException("Tramo no encontrado"));
        if (diasSubsidio != null && diasSubsidio > 0) {
            tramo.setDiasSubsidio(diasSubsidio);
        }
        if (diasLaborados != null && diasLaborados >= 0) {
            tramo.setDiasLaborados(diasLaborados);
        }
        return tramoRepository.save(tramo);
    }

    @Transactional(readOnly = true)
    public List<SubsidioTramo> listarPorCaso(Long casoId) {
        return tramoRepository.findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(
                casoId, 1, "S");
    }
}
