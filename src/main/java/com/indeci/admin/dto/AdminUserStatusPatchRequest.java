package com.indeci.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserStatusPatchRequest {

    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "Estado debe ser ACTIVE o INACTIVE")
    private String status;
}
