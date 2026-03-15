package com.familyhub.demo.service;

import com.familyhub.demo.config.GoogleOAuthConfig;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.model.GoogleOAuthToken;
import com.familyhub.demo.repository.GoogleOAuthTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class GoogleOAuthService {
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String SCOPES = "https://www.googleapis.com/auth/calendar.events.readonly " +
            "https://www.googleapis.com/auth/calendar.calendarlist.readonly";

    private final GoogleOAuthConfig config;
    private final GoogleOAuthTokenRepository tokenRepository;
    private final TokenEncryptionService encryptionService;
    private final OAuthStateStore stateStore;
    private final RestClient restClient;

    @Autowired
    public GoogleOAuthService(GoogleOAuthConfig config,
                              GoogleOAuthTokenRepository tokenRepository,
                              TokenEncryptionService encryptionService,
                              OAuthStateStore stateStore) {
        this(config, tokenRepository, encryptionService, stateStore, RestClient.create());
    }

    public GoogleOAuthService(GoogleOAuthConfig config,
                              GoogleOAuthTokenRepository tokenRepository,
                              TokenEncryptionService encryptionService,
                              OAuthStateStore stateStore,
                              RestClient restClient) {
        this.config = config;
        this.tokenRepository = tokenRepository;
        this.encryptionService = encryptionService;
        this.stateStore = stateStore;
        this.restClient = restClient;
    }

    public String buildAuthorizationUrl(UUID memberId) {
        String state = stateStore.generateState(memberId);
        return AUTH_URL + "?" +
                "client_id=" + encode(config.getClientId()) +
                "&redirect_uri=" + encode(config.getRedirectUri()) +
                "&response_type=code" +
                "&scope=" + encode(SCOPES) +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + encode(state);
    }

    public Optional<UUID> consumeState(String state) {
        return stateStore.consumeState(state);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public GoogleOAuthToken exchangeCodeForTokens(String code, FamilyMember member) {
        Map<String, Object> response = restClient.post()
                .uri(TOKEN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("code=" + encode(code) +
                        "&client_id=" + encode(config.getClientId()) +
                        "&client_secret=" + encode(config.getClientSecret()) +
                        "&redirect_uri=" + encode(config.getRedirectUri()) +
                        "&grant_type=authorization_code")
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new BadRequestException("Failed to exchange authorization code for tokens");
        }

        String accessToken = (String) response.get("access_token");
        String refreshToken = (String) response.get("refresh_token");
        Integer expiresIn = (Integer) response.get("expires_in");
        String scope = (String) response.get("scope");

        // Delete existing token if reconnecting (flush to avoid unique constraint violation)
        tokenRepository.findByMemberId(member.getId())
                .ifPresent(existing -> {
                    tokenRepository.delete(existing);
                    tokenRepository.flush();
                });

        GoogleOAuthToken token = new GoogleOAuthToken();
        token.setMember(member);
        token.setAccessToken(encryptionService.encrypt(accessToken));
        token.setRefreshToken(encryptionService.encrypt(refreshToken));
        token.setTokenExpiry(Instant.now().plusSeconds(expiresIn != null ? expiresIn : 3600));
        token.setScope(scope != null ? scope : SCOPES);

        return tokenRepository.save(token);
    }

    @Transactional
    public void disconnect(UUID memberId) {
        tokenRepository.findByMemberId(memberId).ifPresent(token -> {
            // Best-effort revoke at Google
            try {
                String accessToken = encryptionService.decrypt(token.getAccessToken());
                restClient.post()
                        .uri(REVOKE_URL + "?token=" + encode(accessToken))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("Failed to revoke Google token for member {}: {}", memberId, e.getMessage());
            }

            tokenRepository.delete(token);
        });
    }

    public boolean isConnected(UUID memberId) {
        return tokenRepository.findByMemberId(memberId).isPresent();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
