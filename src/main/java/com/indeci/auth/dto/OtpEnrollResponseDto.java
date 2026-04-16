package com.indeci.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Respuesta para el enroll de OTP.
 *
 * Contiene la imagen QR que el frontend debe mostrar
 * para que el usuario escanee con Google Authenticator.
 */
@Getter
@AllArgsConstructor
public class OtpEnrollResponseDto {

    /**
     * Imagen QR en Base64 o URL lista para mostrar:
     * <img src="qrImage" />
     */
    private String qrImage;
}