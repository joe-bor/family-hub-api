package com.familyhub.demo.service;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.config.JwtConfig;
import com.familyhub.demo.model.Family;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtConfig jwtConfig;
    private Family family;

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hhLTI1Ng==";

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret(SECRET);
        jwtConfig.setExpiration(86400000L);
        jwtConfig.setIssuer("family-hub-api");

        jwtService = new JwtService(jwtConfig);
        family = TestDataFactory.createFamily();
    }

    @Test
    void generateToken_containsCorrectSubject() {
        String token = jwtService.generateToken(family);

        String subject = jwtService.extractSubject(token);
        assertThat(subject).isEqualTo(family.getId().toString());
    }

    @Test
    void extractSubject_validToken() {
        String token = jwtService.generateToken(family);

        String subject = jwtService.extractSubject(token);

        assertThat(subject).isEqualTo(family.getId().toString());
    }

    @Test
    void extractSubject_expiredToken_throws() {
        JwtConfig expiredConfig = new JwtConfig();
        expiredConfig.setSecret(SECRET);
        expiredConfig.setExpiration(0L);
        expiredConfig.setIssuer("family-hub-api");
        JwtService expiredJwtService = new JwtService(expiredConfig);

        String token = expiredJwtService.generateToken(family);

        assertThatThrownBy(() -> jwtService.extractSubject(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractSubject_tamperedToken_throws() {
        String token = jwtService.generateToken(family);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.extractSubject(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractSubject_wrongIssuer_throws() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .subject(family.getId().toString())
                .issuer("wrong-issuer")
                .signWith(key)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .compact();

        assertThatThrownBy(() -> jwtService.extractSubject(token))
                .isInstanceOf(JwtException.class);
    }
}
