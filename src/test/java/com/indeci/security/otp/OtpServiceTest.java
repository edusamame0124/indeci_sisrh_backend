package com.indeci.security.otp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OtpServiceTest {

    @Test
    void generarUrlQr_devuelveDataUrlPngBase64() {
        OtpService svc = new OtpService();
        String otpUrl = "otpauth://totp/APP:USER?secret=JBSWY3DPEHPK3PXP&issuer=APP";
        String data = svc.generarUrlQr(otpUrl);
        assertTrue(data.startsWith("data:image/png;base64,"));
        assertTrue(data.length() > 64);
    }
}
