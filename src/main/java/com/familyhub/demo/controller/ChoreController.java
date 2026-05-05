package com.familyhub.demo.controller;

import com.familyhub.demo.dto.ApiResponse;
import com.familyhub.demo.dto.ChoreResponse;
import com.familyhub.demo.dto.CreateChoreRequest;
import com.familyhub.demo.dto.UpdateChoreRequest;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.service.ChoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chores")
@RequiredArgsConstructor
public class ChoreController {
    private final ChoreService choreService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChoreResponse>>> getChores(@AuthenticationPrincipal Family family) {
        return ResponseEntity.ok(new ApiResponse<>(choreService.getChores(family), ""));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChoreResponse>> createChore(
            @Valid @RequestBody CreateChoreRequest request,
            @AuthenticationPrincipal Family family
    ) {
        ChoreResponse response = choreService.createChore(request, family);

        return ResponseEntity.created(URI.create("/api/chores/" + response.id()))
                .body(new ApiResponse<>(response, "Chore created successfully"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ChoreResponse>> updateChore(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChoreRequest request,
            @AuthenticationPrincipal Family family
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(choreService.updateChore(id, request, family), "Chore updated successfully")
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChore(
            @PathVariable UUID id,
            @AuthenticationPrincipal Family family
    ) {
        choreService.deleteChore(id, family);
        return ResponseEntity.noContent().build();
    }
}
