package com.familyhub.demo.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockFamilySecurityContextFactory.class)
public @interface WithMockFamily {
    String id() default "00000000-0000-0000-0000-000000000001";
    String name() default "Test Family";
    String username() default "testfamily";
}
