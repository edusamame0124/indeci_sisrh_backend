package com.indeci.auth.dto;

import java.util.List;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private String refreshToken;
    private String newPass;
    private List<String> roles;
    private List<String> permisos;
    private boolean requiereOtp;
    private boolean requiereEnroll;

}