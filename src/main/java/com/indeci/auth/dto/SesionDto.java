package com.indeci.auth.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SesionDto {
    private Long id;
    private String ip;
    private String userAgent;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaExpiracion;
    private boolean actual;
}