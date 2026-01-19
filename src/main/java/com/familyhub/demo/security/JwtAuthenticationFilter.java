package com.familyhub.demo.security;

import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String JWT_ERROR_ATTRIBUTE = "jwt-error";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final JwtService jwtService;
    private final FamilyService familyService;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // Check incoming request for JWT in header
        String token = resolveToken(request);

        // Attempt authentication
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 1.) validate
                String username = jwtService.extractUsername(token);
                UserDetails userDetails = familyService.findByUsername(username);
                jwtService.validateToken(token, userDetails);

                // 2.) Set security context
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                // Store error in request object
                request.setAttribute(JWT_ERROR_ATTRIBUTE, e);
                // TODO: create AuthenticationEntryPoint & use it in SecurityConfig to handle this error
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}