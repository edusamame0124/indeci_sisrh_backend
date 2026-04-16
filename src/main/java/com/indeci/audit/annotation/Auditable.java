package com.indeci.audit.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    String accion(); // LOGIN, REFRESH, OTP, etc.
}