package com.indeci.admin.dto;

import lombok.Data;

/**
 * Spec 011 / B2 — Cuerpo PUT para vincular una cuenta con un empleado.
 * {@code empleadoId} nulo desvincula la cuenta.
 */
@Data
public class AdminUserEmpleadoPutRequest {

    private Long empleadoId;
}
