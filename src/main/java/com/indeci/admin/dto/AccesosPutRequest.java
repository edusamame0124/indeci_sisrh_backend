package com.indeci.admin.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccesosPutRequest(
        @NotNull
        @Size(max = 20)
        List<@Valid AccesoSistemaPutItem> accesos) {
}
