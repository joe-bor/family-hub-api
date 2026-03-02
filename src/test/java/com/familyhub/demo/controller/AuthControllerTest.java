package com.familyhub.demo.controller;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.config.JwtConfig;
import com.familyhub.demo.dto.AuthResponse;
import com.familyhub.demo.dto.FamilyResponse;
import com.familyhub.demo.dto.UsernameCheckResponse;
import com.familyhub.demo.exception.InvalidCredentialException;
import com.familyhub.demo.exception.UsernameAlreadyExists;
import com.familyhub.demo.security.JwtAuthenticationEntryPoint;
import com.familyhub.demo.security.JwtAuthenticationFilter;
import com.familyhub.demo.service.AuthService;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // Required by security filter chain
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private FamilyService familyService;
    @MockitoBean
    private JwtConfig jwtConfig;

    @Test
    void register_returns200() throws Exception {
        FamilyResponse familyResponse = new FamilyResponse(
                TestDataFactory.FAMILY_ID, "Test Family", List.of(), LocalDateTime.now());
        AuthResponse authResponse = new AuthResponse("jwt-token", familyResponse);
        when(authService.register(any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "testfamily",
                                    "password": "password123",
                                    "familyName": "Test Family",
                                    "members": [{"name": "John", "color": "coral"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.message").value("Register successful"));
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        when(authService.register(any())).thenThrow(new UsernameAlreadyExists("testfamily"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "testfamily",
                                    "password": "password123",
                                    "familyName": "Test Family",
                                    "members": [{"name": "John", "color": "coral"}]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username testfamily already exists."));
    }

    @Test
    void register_validationError_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "",
                                    "password": "short",
                                    "familyName": "",
                                    "members": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void login_returns200() throws Exception {
        FamilyResponse familyResponse = new FamilyResponse(
                TestDataFactory.FAMILY_ID, "Test Family", List.of(), LocalDateTime.now());
        AuthResponse authResponse = new AuthResponse("jwt-token", familyResponse);
        when(authService.login(any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "testfamily",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.message").value("Login successful"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialException("Invalid username or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "testfamily",
                                    "password": "wrongpassword"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkUsername_returns200() throws Exception {
        when(authService.checkUsername("testfamily")).thenReturn(new UsernameCheckResponse(true));

        mockMvc.perform(get("/api/auth/check-username")
                        .param("username", "testfamily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.message").value("Username check"));
    }
}
