package com.familyhub.demo.service;

import com.familyhub.demo.config.GoogleOAuthConfig;
import com.familyhub.demo.model.GoogleOAuthToken;
import com.familyhub.demo.repository.GoogleOAuthTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock
    private GoogleOAuthTokenRepository tokenRepository;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private RestClient restClient;

    @Mock
    private OAuthStateStore stateStore;

    private GoogleOAuthConfig config;
    private GoogleOAuthService oauthService;

    @BeforeEach
    void setUp() {
        config = new GoogleOAuthConfig();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setRedirectUri("http://localhost:8080/api/google/callback");

        oauthService = new GoogleOAuthService(config, tokenRepository, encryptionService, stateStore, restClient);
    }

    @Test
    void buildAuthorizationUrl_containsRequiredParams() {
        UUID memberId = UUID.randomUUID();
        String stateToken = UUID.randomUUID().toString();
        when(stateStore.generateState(memberId)).thenReturn(stateToken);

        String url = oauthService.buildAuthorizationUrl(memberId);

        assertThat(url).contains("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(url).contains("client_id=test-client-id");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("access_type=offline");
        assertThat(url).contains("prompt=consent");
        assertThat(url).contains("state=" + stateToken);
        assertThat(url).doesNotContain(memberId.toString());
        assertThat(url).contains("scope=");
        assertThat(url).contains("calendar.events.readonly");
        assertThat(url).contains("calendar.calendarlist.readonly");
    }

    @Test
    void isConnected_withToken_returnsTrue() {
        UUID memberId = UUID.randomUUID();
        when(tokenRepository.findByMemberId(memberId))
                .thenReturn(Optional.of(new GoogleOAuthToken()));

        assertThat(oauthService.isConnected(memberId)).isTrue();
    }

    @Test
    void isConnected_withoutToken_returnsFalse() {
        UUID memberId = UUID.randomUUID();
        when(tokenRepository.findByMemberId(memberId))
                .thenReturn(Optional.empty());

        assertThat(oauthService.isConnected(memberId)).isFalse();
    }

    @Test
    void disconnect_deletesToken() {
        UUID memberId = UUID.randomUUID();
        GoogleOAuthToken token = new GoogleOAuthToken();
        token.setAccessToken("encrypted-access");
        when(tokenRepository.findByMemberId(memberId))
                .thenReturn(Optional.of(token));
        when(encryptionService.decrypt("encrypted-access")).thenReturn("plain-access");

        oauthService.disconnect(memberId);

        verify(tokenRepository).delete(token);
    }

    @Test
    void disconnect_noToken_doesNothing() {
        UUID memberId = UUID.randomUUID();
        when(tokenRepository.findByMemberId(memberId))
                .thenReturn(Optional.empty());

        oauthService.disconnect(memberId);

        verify(tokenRepository, never()).delete(any());
    }
}
