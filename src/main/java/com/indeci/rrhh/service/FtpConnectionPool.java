package com.indeci.rrhh.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Pool de conexiones FTP reutilizables para {@link FtpService}.
 *
 * <p>Antes, cada subida/descarga pagaba un handshake completo (connect + login) por
 * request — identificado como el cuello de botella real de latencia en fotos de perfil
 * y sustentos documentales del legajo. Este pool evita reabrir esa conexión cada vez.
 *
 * <p>Solo se usa cuando {@code FtpService} opera contra el FTP real (no en el fallback
 * de disco local de desarrollo) — con {@code minIdle=0} el pool no intenta conectar
 * nada hasta el primer {@link #borrow()}, así que no rompe entornos sin FTP configurado.
 *
 * <p>Regla de seguridad: una conexión que terminó en estado dudoso (excepción durante
 * el uso) SIEMPRE se invalida vía {@link #invalidar}, nunca se devuelve al pool — evita
 * reutilizar una sesión FTP potencialmente corrupta a mitad de una transferencia.
 */
@Component
@Slf4j
public class FtpConnectionPool {

    @Value("${ftp.host}")
    private String host;

    @Value("${ftp.port}")
    private int port;

    @Value("${ftp.user}")
    private String user;

    @Value("${ftp.password}")
    private String password;

    @Value("${ftp.pool.max-total:5}")
    private int maxTotal;

    @Value("${ftp.pool.max-idle:3}")
    private int maxIdle;

    @Value("${ftp.pool.min-idle:0}")
    private int minIdle;

    @Value("${ftp.pool.max-wait-seconds:10}")
    private int maxWaitSeconds;

    @Value("${ftp.pool.min-evictable-idle-minutes:5}")
    private int minEvictableIdleMinutes;

    private GenericObjectPool<FTPClient> pool;

    @PostConstruct
    void init() {
        GenericObjectPoolConfig<FTPClient> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        // Validar antes de prestar y durante la ronda de eviction — descarta conexiones
        // muertas (timeout del servidor, firewall que cortó la sesión, etc.).
        config.setTestOnBorrow(true);
        config.setTestWhileIdle(true);
        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofSeconds(maxWaitSeconds));
        // Eviction periódica de conexiones ociosas — evita mantener sesiones FTP
        // abiertas indefinidamente si no hay tráfico.
        config.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
        config.setMinEvictableIdleDuration(Duration.ofMinutes(minEvictableIdleMinutes));

        this.pool = new GenericObjectPool<>(new FtpClientFactory(), config);
    }

    /** Toma una conexión lista para usar (validada). Puede bloquear hasta maxWaitSeconds si el pool está lleno. */
    public FTPClient borrow() throws Exception {
        return pool.borrowObject();
    }

    /** La conexión se usó sin errores — puede volver al pool para otro request. */
    public void devolver(FTPClient ftp) {
        if (ftp == null) {
            return;
        }
        try {
            pool.returnObject(ftp);
        } catch (Exception ex) {
            log.warn("Error al devolver conexión FTP al pool", ex);
        }
    }

    /** La conexión terminó en estado dudoso (excepción durante el uso) — descartar, nunca reutilizar. */
    public void invalidar(FTPClient ftp) {
        if (ftp == null) {
            return;
        }
        try {
            pool.invalidateObject(ftp);
        } catch (Exception ex) {
            log.warn("Error al invalidar conexión FTP del pool", ex);
        }
    }

    @PreDestroy
    void shutdown() {
        if (pool != null) {
            pool.close();
        }
    }

    private class FtpClientFactory extends BasePooledObjectFactory<FTPClient> {

        @Override
        public FTPClient create() throws Exception {
            FTPClient ftp = new FTPClient();
            ftp.connect(host, port);
            log.debug("FTP reply (pool): {}", ftp.getReplyString());

            boolean login = ftp.login(user, password);
            if (!login) {
                try {
                    ftp.disconnect();
                } catch (Exception ignored) {
                    // ya está en mal estado, nada que hacer
                }
                throw new IllegalStateException(
                        "No se pudo autenticar en el servidor FTP. "
                                + "Configure INDECI_FTP_USER e INDECI_FTP_PASSWORD.");
            }

            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.setBufferSize(1024 * 1024);
            return ftp;
        }

        @Override
        public PooledObject<FTPClient> wrap(FTPClient ftp) {
            return new DefaultPooledObject<>(ftp);
        }

        @Override
        public boolean validateObject(PooledObject<FTPClient> pooled) {
            FTPClient ftp = pooled.getObject();
            try {
                return ftp.isConnected() && ftp.sendNoOp();
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public void destroyObject(PooledObject<FTPClient> pooled) {
            FTPClient ftp = pooled.getObject();
            try {
                if (ftp.isConnected()) {
                    ftp.logout();
                    ftp.disconnect();
                }
            } catch (Exception ex) {
                log.warn("Error al cerrar conexión FTP del pool", ex);
            }
        }
    }
}
