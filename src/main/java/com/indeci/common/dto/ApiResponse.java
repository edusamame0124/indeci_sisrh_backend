package com.indeci.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {

    private String estado;
    private String mensaje;
    private T data;
}