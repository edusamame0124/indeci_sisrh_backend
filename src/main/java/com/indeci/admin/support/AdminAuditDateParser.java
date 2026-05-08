package com.indeci.admin.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import com.indeci.exception.NegocioException;

public final class AdminAuditDateParser {

    private AdminAuditDateParser() {
    }

    public static LocalDateTime parseInicio(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDate.parse(t).atStartOfDay();
            } catch (DateTimeParseException e2) {
                throw new NegocioException("fechaDesde inválida");
            }
        }
    }

    public static LocalDateTime parseFin(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDate.parse(t).atTime(23, 59, 59);
            } catch (DateTimeParseException e2) {
                throw new NegocioException("fechaHasta inválida");
            }
        }
    }
}
