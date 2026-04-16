package com.indeci.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoginRateLimiter
 *
 * Implementa un rate limiting por IP utilizando Bucket4j.
 *
 * Objetivo:
 * Proteger el endpoint de login contra ataques de fuerza bruta
 * limitando la cantidad de intentos por dirección IP.
 *
 * Estrategia actual:
 * - 5 intentos por IP
 * - Ventana de 1 minuto
 *
 * Si se excede el límite:
 * - Se devuelve HTTP 429 (Too Many Requests)
 */
@Component
public class LoginRateLimiter {

    /**
     * Cache en memoria que asocia:
     * IP -> Bucket (contador de intentos)
     *
     * ConcurrentHashMap permite acceso seguro en entorno multi-hilo.
     */
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();


    /**
     * Crea un nuevo Bucket (balde de tokens).
     *
     * ¿Cómo funciona?
     * - Capacidad: 5 tokens
     * - Cada intento consume 1 token
     * - Se recargan 5 tokens cada 1 minuto
     *
     * Ejemplo:
     * 5 intentos permitidos en 1 minuto.
     * El sexto intento dentro del mismo minuto será bloqueado.
     */
    private Bucket newBucket() {

        // Definimos el límite:
        // classic(capacidad, recarga)
        Bandwidth limit = Bandwidth.classic(
                5,                                      // Máximo 5 intentos
                Refill.intervally(5, Duration.ofMinutes(1)) // Se recargan 5 tokens cada 1 minuto
        );

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }


    /**
     * Intenta consumir 1 intento para la IP dada.
     *
     * @param ip Dirección IP del cliente
     * @return true  -> permitido
     *         false -> límite alcanzado (bloquear)
     */
    public boolean tryConsume(String ip) {

        // Si la IP no existe en el cache, se crea un nuevo bucket
        Bucket bucket = cache.computeIfAbsent(ip, k -> newBucket());

        // Intenta consumir 1 token
        return bucket.tryConsume(1);
    }
    
    /**
     * 🔥 NUEVO MÉTODO CLAVE
     * Devuelve cuántos intentos ya se hicieron
     */
    public int obtenerIntentos(String ip) {

        Bucket bucket = cache.computeIfAbsent(ip, k -> newBucket());

        // Tokens restantes
        long disponibles = bucket.getAvailableTokens();

        // Capacidad total = 5
        int intentos = (int) (5 - disponibles);

        return intentos;
    }
    
    public void reset(String key) {
    	cache.remove(key);
    }
}