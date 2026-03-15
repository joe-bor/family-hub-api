package com.familyhub.demo.controller;

import com.familyhub.demo.config.GoogleOAuthConfig;
import com.familyhub.demo.dto.ApiResponse;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.service.FamilyMemberService;
import com.familyhub.demo.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/google")
@RequiredArgsConstructor
public class GoogleOAuthController {
    private final GoogleOAuthService googleOAuthService;
    private final GoogleOAuthConfig googleOAuthConfig;
    private final FamilyMemberService familyMemberService;

    @GetMapping("/auth")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAuthorizationUrl(
            @RequestParam UUID memberId,
            @AuthenticationPrincipal Family family) {
        familyMemberService.findById(family, memberId);

        String url = googleOAuthService.buildAuthorizationUrl(memberId);
        return ResponseEntity.ok(new ApiResponse<>(
                Map.of("authorizationUrl", url),
                "Authorization URL generated"));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam String code,
            @RequestParam String state) {
        UUID memberId = googleOAuthService.consumeState(state)
                .orElseThrow(() -> new BadRequestException("Invalid or expired OAuth state"));

        googleOAuthService.exchangeCodeForTokens(code, memberId);

        // Redirect to frontend settings page
        return ResponseEntity.status(302)
                .location(URI.create(googleOAuthConfig.getFrontendRedirectUrl()))
                .build();
    }

    @DeleteMapping("/disconnect/{memberId}")
    public ResponseEntity<ApiResponse<Void>> disconnect(
            @PathVariable UUID memberId,
            @AuthenticationPrincipal Family family) {
        familyMemberService.findById(family, memberId);

        googleOAuthService.disconnect(memberId);
        return ResponseEntity.ok(new ApiResponse<>(null, "Google account disconnected"));
    }

    @GetMapping("/status/{memberId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getConnectionStatus(
            @PathVariable UUID memberId,
            @AuthenticationPrincipal Family family) {
        familyMemberService.findById(family, memberId);

        boolean connected = googleOAuthService.isConnected(memberId);
        return ResponseEntity.ok(new ApiResponse<>(
                Map.of("connected", connected),
                null));
    }
}
