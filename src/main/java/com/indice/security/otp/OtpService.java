package com.indice.security.otp;

import com.warrenstrange.googleauth.GoogleAuthenticator;
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

    /**
     * Instancia del motor TOTP.
     *
     * Por defecto usa:
     * - SHA1
     * - 6 dígitos
     * - Ventana de tiempo estándar (~30 segundos)
     */
    private final GoogleAuthenticator googleAuthenticator =
            new GoogleAuthenticator();

    /**
     * Genera un nuevo secreto OTP.
     *
     * Este secret debe:
     * - Guardarse en base de datos (cuando se confirma enroll).
     * - Usarse para validar códigos posteriores.
     *
     * @return GoogleAuthenticatorKey que contiene:
     *         - secret
     *         - código inicial
     *         - información adicional
     */
    public GoogleAuthenticatorKey generarSecret() {
        return googleAuthenticator.createCredentials();
    }

    /**
     * Valida un código OTP ingresado por el usuario.
     *
     * @param secret Secret almacenado del usuario.
     * @param codigo Código de 6 dígitos ingresado.
     * @return true si el código es válido en la ventana de tiempo actual.
     *
     * Nota:
     * La librería permite pequeña tolerancia de tiempo
     * para compensar desincronización de reloj.
     */
    public boolean validarCodigo(String secret, int codigo) {
        return googleAuthenticator.authorize(secret, codigo);
    }

    /**
     * Construye la URL estándar "otpauth://" usada para generar el QR.
     *
     * Esta URL es interpretada por aplicaciones autenticadoras.
     *
     * Formato:
     * otpauth://totp/{app}:{usuario}?secret={secret}&issuer={app}
     *
     * @param appName Nombre del sistema (ej: SIGCO).
     * @param usuario Identificador del usuario.
     * @param secret Secret generado.
     * @return URL en formato estándar OTP.
     */
    public String construirOtpAuthUrl(
            String appName,
            String usuario,
            String secret
    ) {
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s",
                appName,
                usuario,
                secret,
                appName
        );
    }
}