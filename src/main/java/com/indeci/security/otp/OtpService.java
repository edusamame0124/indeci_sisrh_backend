package com.indeci.security.otp;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.extern.slf4j.Slf4j;

/**
 * OTP (TOTP). Generación de QR en proceso (ZXing) — sin servicios HTTP externos.
 */
@Slf4j
@Component
public class OtpService {

    private static final int QR_SIZE = 200;

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

    /**
     * Genera PNG en data URL (base64) para el payload otpauth://…
     * (el frontend muestra la imagen sin exponer el secreto a terceros).
     */
    public String generarUrlQr(String otpAuthUrl) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpAuthUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            String b64 = Base64.getEncoder().encodeToString(png.toByteArray());
            return "data:image/png;base64," + b64;
        } catch (WriterException e) {
            log.error("No se pudo generar QR local", e);
            throw new IllegalStateException("Error al generar código QR");
        } catch (java.io.IOException e) {
            log.error("No se pudo escribir PNG del QR", e);
            throw new IllegalStateException("Error al generar código QR");
        }
    }
}
