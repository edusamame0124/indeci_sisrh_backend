package com.indeci.security.otp;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.stereotype.Component;

/**
 * Servicio encargado de la lógica criptográfica del
 * segundo factor de autenticación (OTP - TOTP).
 *
 * Utiliza la librería:
 * com.warrenstrange.googleauth
 *
 * Implementa el estándar:
 * TOTP (Time-based One-Time Password)
 * compatible con:
 * - Google Authenticator
 * - Microsoft Authenticator
 * - Authy
 * - Apps compatibles con RFC 6238
 *
 * Este servicio:
 * - Genera secretos OTP.
 * - Valida códigos ingresados por el usuario.
 * - Construye URL estándar otpauth:// para generación de QR.
 *
 * Este componente NO:
 * - Persiste secretos.
 * - Genera QR.
 * - Maneja auditoría.
 *
 * Solo realiza operaciones criptográficas.
 */
@Component
public class OtpService {

    private final GoogleAuthenticator googleAuthenticator;

    public OtpService() {
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig
                .GoogleAuthenticatorConfigBuilder()
                .setCodeDigits(6)
                .setTimeStepSizeInMillis(30000)
                .setWindowSize(3)
                .build();

        this.googleAuthenticator = new GoogleAuthenticator(config);
    }

    public GoogleAuthenticatorKey generarSecret() {
        return googleAuthenticator.createCredentials();
    }

    public boolean validarCodigo(String secret, int codigo) {

        if (secret == null || secret.isBlank()) {
            return false;
        }

        if (codigo <= 0) {
            return false;
        }

        return googleAuthenticator.authorize(secret, codigo);
    }

    public String construirOtpAuthUrl(String appName, String usuario, String secret) {

        String user = usuario.trim().toUpperCase();

        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s",
                appName,
                user,
                secret,
                appName
        );
    }

    public String generarUrlQr(String otpAuthUrl) {
        return "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=" + otpAuthUrl;
    }
}