package com.indeci.admin.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserPermisoGrantsPutRequest {

    @NotNull
    private List<Long> permisoIds;
}
