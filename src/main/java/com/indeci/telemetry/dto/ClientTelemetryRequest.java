package com.indeci.telemetry.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientTelemetryRequest {

    @NotBlank
    private String category;

    private String ts;

    private String url;

    private Integer status;

    private String mensaje;

    private String username;

    private Map<String, Object> extra;
}
