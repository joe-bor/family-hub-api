package com.familyhub.demo.controller;

import com.familyhub.demo.config.GoogleOAuthConfig;
import com.familyhub.demo.config.SecurityConfig;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.model.FamilyColor;
import com.familyhub.demo.repository.FamilyMemberRepository;
import com.familyhub.demo.security.JwtAuthenticationEntryPoint;
import com.familyhub.demo.security.JwtAuthenticationFilter;
import com.familyhub.demo.security.WithMockFamily;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.GoogleOAuthService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.FAMILY_ID;
import static com.familyhub.demo.TestDataFactory.MEMBER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GoogleOAuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
@ActiveProfiles("test")
class GoogleOAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FamilyService familyService;

    @MockitoBean
    private GoogleOAuthService googleOAuthService;

    @MockitoBean
    private FamilyMemberRepository familyMemberRepository;

    @MockitoBean
    private GoogleOAuthConfig googleOAuthConfig;

    private FamilyMember createMember() {
        Family family = new Family();
        family.setId(FAMILY_ID);
        FamilyMember member = new FamilyMember();
        member.setId(MEMBER_ID);
        member.setFamily(family);
        member.setName("Test Member");
        member.setColor(FamilyColor.CORAL);
        return member;
    }

    @Test
    @WithMockFamily
    void getAuthorizationUrl_returnUrl() throws Exception {
        FamilyMember member = createMember();
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(googleOAuthService.buildAuthorizationUrl(MEMBER_ID))
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?client_id=test");

        mockMvc.perform(get("/api/google/auth").param("memberId", MEMBER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationUrl").value(
                        "https://accounts.google.com/o/oauth2/v2/auth?client_id=test"));
    }

    @Test
    @WithMockFamily
    void getAuthorizationUrl_wrongFamily_returns403() throws Exception {
        Family otherFamily = new Family();
        otherFamily.setId(UUID.randomUUID());
        FamilyMember member = new FamilyMember();
        member.setId(MEMBER_ID);
        member.setFamily(otherFamily);
        member.setName("Other Member");
        member.setColor(FamilyColor.TEAL);

        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        mockMvc.perform(get("/api/google/auth").param("memberId", MEMBER_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockFamily
    void getStatus_connected_returnsTrue() throws Exception {
        FamilyMember member = createMember();
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(googleOAuthService.isConnected(MEMBER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/google/status/{memberId}", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    @WithMockFamily
    void getStatus_disconnected_returnsFalse() throws Exception {
        FamilyMember member = createMember();
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(googleOAuthService.isConnected(MEMBER_ID)).thenReturn(false);

        mockMvc.perform(get("/api/google/status/{memberId}", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    @WithMockFamily
    void disconnect_removesToken() throws Exception {
        FamilyMember member = createMember();
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        mockMvc.perform(delete("/api/google/disconnect/{memberId}", MEMBER_ID))
                .andExpect(status().isOk());

        verify(googleOAuthService).disconnect(MEMBER_ID);
    }

    @Test
    @WithMockFamily
    void getStatus_wrongFamily_returns403() throws Exception {
        Family otherFamily = new Family();
        otherFamily.setId(UUID.randomUUID());
        FamilyMember member = new FamilyMember();
        member.setId(MEMBER_ID);
        member.setFamily(otherFamily);
        member.setName("Other Member");
        member.setColor(FamilyColor.TEAL);

        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        mockMvc.perform(get("/api/google/status/{memberId}", MEMBER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void callback_validState_redirectsToFrontend() throws Exception {
        String stateToken = UUID.randomUUID().toString();
        FamilyMember member = createMember();
        when(googleOAuthService.consumeState(stateToken)).thenReturn(Optional.of(MEMBER_ID));
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(googleOAuthConfig.getFrontendRedirectUrl())
                .thenReturn("http://localhost:5173/settings?googleConnected=true");

        mockMvc.perform(get("/api/google/callback")
                        .param("code", "test-auth-code")
                        .param("state", stateToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:5173/settings?googleConnected=true"));

        verify(googleOAuthService).exchangeCodeForTokens(eq("test-auth-code"), any(FamilyMember.class));
    }

    @Test
    void callback_invalidState_returns400() throws Exception {
        when(googleOAuthService.consumeState("bogus-state")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/google/callback")
                        .param("code", "test-auth-code")
                        .param("state", "bogus-state"))
                .andExpect(status().isBadRequest());
    }
}
