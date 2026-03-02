package com.familyhub.demo.service;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.dto.AuthResponse;
import com.familyhub.demo.dto.LoginRequest;
import com.familyhub.demo.dto.RegisterRequest;
import com.familyhub.demo.dto.UsernameCheckResponse;
import com.familyhub.demo.exception.InvalidCredentialException;
import com.familyhub.demo.exception.UsernameAlreadyExists;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.repository.FamilyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private Family family;

    @BeforeEach
    void setUp() {
        family = TestDataFactory.createFamily();
    }

    @Test
    void register_success() {
        RegisterRequest request = TestDataFactory.createRegisterRequest();
        when(familyRepository.existsByUsername(request.username())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(familyRepository.save(any(Family.class))).thenReturn(family);
        when(jwtService.generateToken(any(Family.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.family()).isNotNull();
        verify(familyRepository).save(any(Family.class));
    }

    @Test
    void register_duplicateUsername_throws() {
        RegisterRequest request = TestDataFactory.createRegisterRequest();
        when(familyRepository.existsByUsername(request.username())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UsernameAlreadyExists.class)
                .hasMessageContaining(request.username());

        verify(familyRepository, never()).save(any());
    }

    @Test
    void login_success() {
        LoginRequest request = TestDataFactory.createLoginRequest();
        when(familyRepository.findByUsername(request.username())).thenReturn(Optional.of(family));
        when(passwordEncoder.matches(request.password(), family.getPassword())).thenReturn(true);
        when(jwtService.generateToken(family)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.family()).isNotNull();
    }

    @Test
    void login_invalidCredentials_throws() {
        LoginRequest request = TestDataFactory.createLoginRequest();
        when(familyRepository.findByUsername(request.username())).thenReturn(Optional.of(family));
        when(passwordEncoder.matches(request.password(), family.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialException.class);
    }

    @Test
    void login_unknownUsername_throws() {
        LoginRequest request = TestDataFactory.createLoginRequest();
        when(familyRepository.findByUsername(request.username())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialException.class);
    }

    @Test
    void checkUsername_available() {
        when(familyRepository.existsByUsername("newuser")).thenReturn(false);

        UsernameCheckResponse response = authService.checkUsername("newuser");

        assertThat(response.available()).isTrue();
    }

    @Test
    void checkUsername_taken() {
        when(familyRepository.existsByUsername("taken")).thenReturn(true);

        UsernameCheckResponse response = authService.checkUsername("taken");

        assertThat(response.available()).isFalse();
    }
}
