package com.indeci.audit.context;

import org.springframework.stereotype.Component;

@Component
public class AuditoriaContext {

    private static final ThreadLocal<String> detalle = new ThreadLocal<>();

    public void setDetalle(String d) {
        detalle.set(d);
    }

    public String getDetalle() {
        return detalle.get();
    }

    public void clear() {
        detalle.remove();
    }
}