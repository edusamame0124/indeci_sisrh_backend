package com.indeci.auth.dto;

import lombok.Data;

@Data
public class RefreshRequest {
    private String refreshToken;
}