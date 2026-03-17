package com.familyhub.demo.controller;

import com.familyhub.demo.dto.ApiResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.service.FamilyMemberService;
import com.familyhub.demo.service.GoogleCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/google/sync")
@RequiredArgsConstructor
public class GoogleSyncController {
    private final FamilyMemberService familyMemberService;
    private final GoogleCalendarSyncService syncService;

    @PostMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> syncMember(
            @PathVariable UUID memberId,
            @AuthenticationPrincipal Family family) {
        familyMemberService.findById(family, memberId);

        syncService.syncMember(memberId);
        return ResponseEntity.ok(new ApiResponse<>(null, "Sync completed"));
    }
}
