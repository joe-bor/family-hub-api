package com.familyhub.demo.service;

import com.familyhub.demo.dto.AuthResponse;
import com.familyhub.demo.dto.RegisterRequest;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
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
            throw new RuntimeException("Username " + registerRequest.username() + " already exists");
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

}
