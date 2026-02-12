package com.familyhub.demo.controller;

import com.familyhub.demo.dto.FamilyMemberRequest;
import com.familyhub.demo.dto.FamilyMemberResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.service.FamilyMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/family/members")
@RequiredArgsConstructor
public class FamilyMemberController {
    private final FamilyMemberService familyMemberService;


    @GetMapping
    public ResponseEntity<List<FamilyMemberResponse>> getFamilyMembers(@AuthenticationPrincipal Family family) {
        List<FamilyMemberResponse> members = familyMemberService.findAllMembers(family);

        return members.isEmpty() ?
        ResponseEntity.noContent().build() :
        ResponseEntity.ok(members);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FamilyMemberResponse> getFamilyMember(
            @AuthenticationPrincipal Family family,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(familyMemberService.findById(family, id));
    }

    @PostMapping
    public ResponseEntity<FamilyMemberResponse> addFamilyMember(
            @AuthenticationPrincipal Family family,
            @Valid @RequestBody FamilyMemberRequest request) {

        FamilyMember familyMember = familyMemberService.addFamilyMember(family, request);
        URI location =  URI.create("/api/family/members/" + familyMember.getId());

        return ResponseEntity.created(location).body(FamilyMemberResponse.toDto(familyMember));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FamilyMemberResponse> updateFamilyMember(
            @AuthenticationPrincipal Family family,
            @Valid @RequestBody FamilyMemberRequest familyMemberRequest,
            @PathVariable UUID id
    ) {
        FamilyMember familyMember = familyMemberService.updateFamilyMember(family, id, familyMemberRequest);
        return ResponseEntity.ok(FamilyMemberResponse.toDto(familyMember));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFamilyMember(
            @AuthenticationPrincipal Family family,
            @PathVariable UUID id
    ) {
        familyMemberService.deleteFamilyMember(family, id);
        return ResponseEntity.noContent().build();
    }
}
