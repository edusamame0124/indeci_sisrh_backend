package com.indeci.audit.aspect;

import com.indeci.audit.annotation.Auditable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.indeci.audit.entity.Auditoria;
import com.indeci.audit.repository.AuditoriaRepository;
import com.indeci.auth.dto.LoginRequest;
import com.indeci.util.ClientInfoUtil;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import com.indeci.audit.context.AuditoriaContext;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditoriaAspect {

    private final AuditoriaRepository auditoriaRepository;
    private final HttpServletRequest request;
    private final ClientInfoUtil clientInfoUtil;
    private final AuditoriaContext auditoriaContext;

    @Around("@annotation(auditable)")
    public Object auditar(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {

        String usuario = "ANONIMO";
        Object[] args = joinPoint.getArgs();

        for (Object arg : args) {
            if (arg instanceof LoginRequest loginRequest) {
                usuario = loginRequest.getUsername();
            }
        }

        try {
        	Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        	if (auth != null && auth.isAuthenticated()) {
        	    usuario = auth.getName(); // 🔥 SOLO EL USERNAME
        	}
        } catch (Exception ignored) {}

        String ip = clientInfoUtil.obtenerIpReal(request);
        String userAgent = clientInfoUtil.obtenerUserAgent(request);

        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(usuario);
        auditoria.setAccion(auditable.accion());
        auditoria.setMetodo(joinPoint.getSignature().getName());
        auditoria.setIp(ip);
        auditoria.setUserAgent(userAgent);
        auditoria.setFecha(LocalDateTime.now());

        try {
            Object result = joinPoint.proceed();

            auditoria.setEstado("OK");
            String detalle = auditoriaContext.getDetalle();

            auditoria.setDetalle(
                detalle != null ? detalle : "Ejecución correcta"
            );

            auditoriaRepository.save(auditoria);

            return result;

        } catch (Exception e) {

            auditoria.setEstado("ERROR");
            auditoria.setDetalle(e.getMessage());

            auditoriaRepository.save(auditoria);

            throw e;
        }
    }
}