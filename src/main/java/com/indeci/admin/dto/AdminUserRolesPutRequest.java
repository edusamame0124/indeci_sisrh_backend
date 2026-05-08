package com.indeci.admin.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserRolesPutRequest {

    @NotNull
    private List<Long> roleIds;
}
