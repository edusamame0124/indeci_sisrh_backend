package com.indeci.auth.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.auth.dto.SesionDto;
import com.indeci.auth.entity.AuthRefreshToken;
import com.indeci.auth.repository.AuthRefreshTokenRepository;
import com.indeci.security.jwt.JwtProvider;
import com.indeci.exception.NegocioException;
import com.indeci.exception.SeguridadException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class SesionService {

    private final AuthRefreshTokenRepository refreshRepo;
    private final JwtProvider jwtProvider;
    private final AuditoriaContext auditoriaContext;

    // ============================
    // 🔹 LISTAR SESIONES
    // ============================
    public List<SesionDto> listarSesiones(String authHeader) {

        String tokenJwt = authHeader.replace("Bearer ", "");
        String username = jwtProvider.getSubject(tokenJwt);

        List<AuthRefreshToken> lista = refreshRepo.findByUsuarioAndActivo(username, "S");

        return lista.stream().map(t -> {
            SesionDto dto = new SesionDto();
            dto.setId(t.getId());
            dto.setIp(t.getIp());
            dto.setUserAgent(t.getUserAgent());
            dto.setFechaCreacion(t.getFechaCreacion());
            dto.setFechaExpiracion(t.getFechaExpiracion());

            // 🔥 CLAVE: identificar sesión actual
            dto.setActual(tokenJwt.equals(t.getToken()));

            return dto;
        }).toList();
    }

    // ============================
    // 🔹 LOGOUT POR ID
    // ============================
    @Auditable(accion = "LOGOUT_MANUAL")
    public void cerrarSesion(Long id, String authHeader) {

        String tokenJwt = authHeader.replace("Bearer ", "");
        String username = jwtProvider.getSubject(tokenJwt);

        AuthRefreshToken token = refreshRepo.findById(id)
                .orElseThrow(() -> new NegocioException("Sesión no encontrada"));

        // 🔥 VALIDAR PROPIETARIO
        if (!token.getUsuario().equals(username)) {
            auditoriaContext.setDetalle("Intento de cerrar sesión ajena ID: " + id);
            throw new SeguridadException("No puedes cerrar sesiones de otro usuario");
        }

        // 🔥 VALIDAR ESTADO
        if ("N".equalsIgnoreCase(token.getActivo())) {
            auditoriaContext.setDetalle("Intento de cerrar sesión ya cerrada ID: " + id);
            throw new NegocioException("La sesión ya está cerrada");
        }

        token.setActivo("N");
        token.setFechaRevocacion(LocalDateTime.now());
        token.setMotivoRevocacion("LOGOUT_MANUAL");

        auditoriaContext.setDetalle("Logout manual de sesión ID: " + id);

        refreshRepo.save(token);
    }

    // ============================
    // 🔹 LOGOUT TODAS
    // ============================
    @Auditable(accion = "LOGOUT_ALL")
    public void cerrarTodas(String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String username = jwtProvider.getSubject(token);

        List<AuthRefreshToken> lista = refreshRepo.findByUsuarioAndActivo(username, "S");

        if (lista.isEmpty()) {
            throw new NegocioException("No existen sesiones activas");
        }

        lista.forEach(t -> {
            t.setActivo("N");
            t.setFechaRevocacion(LocalDateTime.now());
            t.setMotivoRevocacion("LOGOUT_ALL");
        });

        auditoriaContext.setDetalle("Logout de todas las sesiones");

        refreshRepo.saveAll(lista);
    }

    // ============================
    // 🔹 LOGOUT OTRAS
    // ============================
    @Auditable(accion = "LOGOUT_OTRAS")
    public void cerrarOtras(String authHeader, String refreshActual) {

        String token = authHeader.replace("Bearer ", "");
        String username = jwtProvider.getSubject(token);

        List<AuthRefreshToken> lista = refreshRepo.findByUsuarioAndActivo(username, "S");

        List<AuthRefreshToken> otras = lista.stream()
                .filter(t -> !t.getToken().equals(refreshActual))
                .toList();

        if (otras.isEmpty()) {
            throw new NegocioException("No hay otras sesiones para cerrar");
        }

        otras.forEach(t -> {
            t.setActivo("N");
            t.setFechaRevocacion(LocalDateTime.now());
            t.setMotivoRevocacion("LOGOUT_OTRAS");
        });

        auditoriaContext.setDetalle(
                "Logout de " + otras.size() + " sesiones (manteniendo la actual)"
        );

        refreshRepo.saveAll(otras);
    }
}