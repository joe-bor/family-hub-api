package com.familyhub.demo.service;

import com.familyhub.demo.dto.AuthResponse;
import com.familyhub.demo.dto.LoginRequest;
import com.familyhub.demo.dto.RegisterRequest;
import com.familyhub.demo.exception.FamilyNotFoundException;
import com.familyhub.demo.exception.UsernameAlreadyExists;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final FamilyRepository familyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest registerRequest) {
        // Check if username already exists
        if (familyRepository.existsByUsername(registerRequest.username())) {
            throw new UsernameAlreadyExists(registerRequest.username());
        }

        // Hash password then save to db
        Family family = new Family();
        family.setName("hard-code family name"); // TODO: this will come from the frontend (form data)
        family.setUsername(registerRequest.username());
        family.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        Family saved = familyRepository.save(family);

        // Create JWT and return response
        String token = jwtService.generateToken(saved);

        return new AuthResponse(token, saved);
    }

    public AuthResponse login(LoginRequest loginRequest) {
        Family family = familyRepository.findByUsername(loginRequest.username())
                .orElseThrow(() -> new FamilyNotFoundException(loginRequest.username()));

        String encodedPasswordRequest = passwordEncoder.encode(loginRequest.password());
        // If the hash of provided password does not match the stored password; then request is unauthorized
        if (!encodedPasswordRequest.equals(family.getPassword())) {
            throw new BadCredentialsException("Unauthorized");
        }

        String token = jwtService.generateToken(family);
        return new AuthResponse(token, family);
    }
}
