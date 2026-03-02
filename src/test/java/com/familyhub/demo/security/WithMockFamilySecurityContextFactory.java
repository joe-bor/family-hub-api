package com.familyhub.demo.security;

import com.familyhub.demo.model.Family;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.ArrayList;
import java.util.UUID;

public class WithMockFamilySecurityContextFactory implements WithSecurityContextFactory<WithMockFamily> {

    @Override
    public SecurityContext createSecurityContext(WithMockFamily annotation) {
        Family family = new Family();
        family.setId(UUID.fromString(annotation.id()));
        family.setName(annotation.name());
        family.setUsername(annotation.username());
        family.setPasswordHash("encoded-password");
        family.setFamilyMembers(new ArrayList<>());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(family, null, family.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        return context;
    }
}
