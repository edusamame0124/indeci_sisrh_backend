package com.indeci.rrhh.controller;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.CalculoRentaRequestDto;
import com.indeci.rrhh.dto.CalculoRentaResponseDto;
import com.indeci.rrhh.service.CuartaCategoriaService;
import com.indeci.rrhh.service.QuintaCategoriaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpuestoRentaControllerTest {

    @Mock
    private CuartaCategoriaService cuartaCategoriaService;

    @Mock
    private QuintaCategoriaService quintaCategoriaService;

    @InjectMocks
    private ImpuestoRentaController controller;

    @Test
    void calcular_ConRegimenCAS_DelegaACuartaCategoria() {
        // Arrange
        CalculoRentaRequestDto request = new CalculoRentaRequestDto();
        request.setEmpleadoId(1L);
        request.setAnioFiscal(2026);
        request.setMes(1);
        request.setRegimenLaboralCodigo("CAS"); // Normalizará a 1057

        CalculoRentaResponseDto mockResponse = new CalculoRentaResponseDto();
        mockResponse.setCategoria("CUARTA");
        mockResponse.setRetencionCalculada(new BigDecimal("100.00"));
        
        when(cuartaCategoriaService.calcular(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<CalculoRentaResponseDto> result = controller.calcularImpuestoRenta(request);

        // Assert
        assertNotNull(result.getBody());
        assertEquals("CUARTA", result.getBody().getCategoria());
        assertEquals(new BigDecimal("100.00"), result.getBody().getRetencionCalculada());
        verify(cuartaCategoriaService).calcular(any());
    }

    @Test
    void calcular_ConRegimen276_DelegaAQuintaCategoria() {
        // Arrange
        CalculoRentaRequestDto request = new CalculoRentaRequestDto();
        request.setEmpleadoId(1L);
        request.setAnioFiscal(2026);
        request.setMes(1);
        request.setRegimenLaboralCodigo("276"); 

        CalculoRentaResponseDto mockResponse = new CalculoRentaResponseDto();
        mockResponse.setCategoria("QUINTA");
        mockResponse.setRetencionCalculada(new BigDecimal("250.00"));
        
        when(quintaCategoriaService.calcular(any())).thenReturn(mockResponse);

        // Act
        ResponseEntity<CalculoRentaResponseDto> result = controller.calcularImpuestoRenta(request);

        // Assert
        assertNotNull(result.getBody());
        assertEquals("QUINTA", result.getBody().getCategoria());
        assertEquals(new BigDecimal("250.00"), result.getBody().getRetencionCalculada());
        verify(quintaCategoriaService).calcular(any());
    }
}
