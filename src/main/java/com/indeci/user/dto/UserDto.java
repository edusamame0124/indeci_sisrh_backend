package com.indeci.user.dto;

import lombok.Data;

@Data
public class UserDto {

    private String username;

    private Long roleId;

    private String password;
    private Long empleadoId;
}