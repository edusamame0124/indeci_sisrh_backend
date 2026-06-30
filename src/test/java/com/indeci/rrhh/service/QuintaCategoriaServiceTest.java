package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.CalculoRentaRequestDto;
import com.indeci.rrhh.dto.CalculoRentaResponseDto;
import com.indeci.rrhh.dto.EmpleadoOtrosIngresosDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuintaCategoriaServiceTest {

    @Mock
    private ParametroRemunerativoService parametroRemunerativoService;

    @Mock
    private OtrosIngresosService otrosIngresosService;

    @Mock
    private com.indeci.rrhh.repository.EmpleadoRepository empleadoRepository;

    @Mock
    private com.indeci.rrhh.repository.EmpleadoPlanillaRepository empleadoPlanillaRepository;

    @Mock
    private com.indeci.rrhh.repository.MovimientoPlanillaRepository movimientoPlanillaRepository;

    @InjectMocks
    private QuintaCategoriaService quintaCategoriaService;

    @Test
    void calcular_SinRentaNeta_RetornaCero() {
        // Arrange
        CalculoRentaRequestDto request = new CalculoRentaRequestDto();
        request.setEmpleadoId(1L);
        request.setAnioFiscal(2026);
        request.setMes(1);
        request.setIngresosMesActual(new BigDecimal("1000")); // RBA = 14,000

        when(parametroRemunerativoService.obtenerValor(eq("UIT"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("5150"));
        when(parametroRemunerativoService.obtenerValor(eq("IR5TA_FACTOR_DEDUCCION"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("7"));
        
        when(otrosIngresosService.obtenerPorEmpleadoYAno(anyLong(), anyInt())).thenReturn(null);

        // Act
        CalculoRentaResponseDto response = quintaCategoriaService.calcular(request);

        // Assert (14000 - 36050 <= 0 -> Retencion 0)
        assertNotNull(response);
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getRetencionCalculada()));
        assertEquals("QUINTA", response.getCategoria());
    }

    @Test
    void calcular_ConRentaNetaTramo1_CalculaRetencionCorrecta() {
        // Arrange
        CalculoRentaRequestDto request = new CalculoRentaRequestDto();
        request.setEmpleadoId(2L);
        request.setAnioFiscal(2026);
        request.setMes(1); // Divisor 12
        // Para tener Renta Neta > 0 y entrar al tramo 1 (hasta 5 UIT = 25,750)
        // 7 UIT = 36,050. Necesitamos RBA > 36,050.
        // Ingreso mensual = 3,000 -> RBA = 42,000
        // Renta Neta = 42,000 - 36,050 = 5,950
        // Impuesto (8% de 5,950) = 476
        // Retencion mensual = 476 / 12 = 39.67
        request.setIngresosMesActual(new BigDecimal("3000")); 

        when(parametroRemunerativoService.obtenerValor(eq("UIT"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("5150"));
        when(parametroRemunerativoService.obtenerValor(eq("IR5TA_FACTOR_DEDUCCION"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("7"));
        
        when(otrosIngresosService.obtenerPorEmpleadoYAno(anyLong(), anyInt())).thenReturn(null);

        // Act
        CalculoRentaResponseDto response = quintaCategoriaService.calcular(request);

        // Assert
        assertNotNull(response);
        assertEquals("QUINTA", response.getCategoria());
        assertEquals(new BigDecimal("39.67"), response.getRetencionCalculada());
    }
}
