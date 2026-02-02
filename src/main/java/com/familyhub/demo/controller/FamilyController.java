package com.familyhub.demo.controller;

import com.familyhub.demo.dto.FamilyResponseDto;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.service.FamilyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FamilyController {
    private final FamilyService familyService;

    @GetMapping("/family")
    ResponseEntity<FamilyResponseDto> findFamilyById(@AuthenticationPrincipal Family family) {
        Family familyById = familyService.findFamilyById(family.getId());
        return ResponseEntity.ok(FamilyResponseDto.toDto(familyById));
    }

    @PreAuthorize("#id == authentication.principal.id")
    @PutMapping("/family/{id}")
    ResponseEntity<FamilyResponseDto> updateFamily(@PathVariable UUID id, @RequestBody @Valid Family family) {
        Family updatedFamily = familyService.updateFamily(id, family);
        return ResponseEntity.ok(FamilyResponseDto.toDto(updatedFamily));
    }

    @PreAuthorize("#id == authentication.principal.id")
    @DeleteMapping("/family/{id}")
    ResponseEntity<Void> deleteFamily(@PathVariable UUID id) {
        familyService.deleteFamily(id);
        return ResponseEntity.noContent().build();
    }
}
