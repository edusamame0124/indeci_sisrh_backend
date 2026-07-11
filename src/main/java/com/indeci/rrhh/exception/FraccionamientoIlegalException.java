package com.indeci.rrhh.exception; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.ResponseStatus; @ResponseStatus(HttpStatus.BAD_REQUEST)
public class FraccionamientoIlegalException extends com.indeci.exception.NegocioException { public FraccionamientoIlegalException(String message) { super(message); } }
