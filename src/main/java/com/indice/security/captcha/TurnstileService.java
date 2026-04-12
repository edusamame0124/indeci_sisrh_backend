package com.indice.security.captcha;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import exception.TurnstileInvalidoException;





@Service
public class TurnstileService {

    private final RestTemplate restTemplate;

    @Value("${cloudflare.turnstile.secret}")
    private String secretKey;

    public TurnstileService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void validarToken(String token) {

        if (token == null || token.isBlank()) {
            throw new TurnstileInvalidoException(); // mejor que RuntimeException
        }

        // Cloudflare espera x-www-form-urlencoded
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                request,
                Map.class
        );

        Object successObj = response.getBody() != null ? response.getBody().get("success") : null;
        boolean success = Boolean.TRUE.equals(successObj);

        if (!success) {
            throw new TurnstileInvalidoException();
        }
    }
}