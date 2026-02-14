package com.familyhub.demo.service;


import com.familyhub.demo.dto.FamilyMemberRequest;
import com.familyhub.demo.dto.FamilyMemberResponse;
import com.familyhub.demo.exception.FamilyMemberNotFound;
import com.familyhub.demo.mapper.FamilyMemberMapper;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyMemberService {
    private final FamilyMemberRepository familyMemberRepository;

    public List<FamilyMemberResponse> findAllMembers(Family family) {
         return familyMemberRepository.findByFamily(family)
                 .stream()
                 .map(FamilyMemberMapper::toDto)
                 .toList();
    }

    public FamilyMemberResponse findById(Family family, UUID familyMemberId) {
        FamilyMember familyMember = findById(familyMemberId);
        if (!isMemberOfFamily(family, familyMember)) {
            throw new AccessDeniedException("Unauthorized");
        }
        return FamilyMemberMapper.toDto(familyMember);
    }

    public FamilyMember addFamilyMember(Family family, FamilyMemberRequest toAdd) {
        return familyMemberRepository.save(FamilyMemberMapper.toEntity(toAdd, family));
    }

    public FamilyMember updateFamilyMember(
            Family family,
            UUID familyMemberId,
            FamilyMemberRequest update) {

        // Check if familyMember with passed uuid exists
        FamilyMember toBeUpdated = findById(familyMemberId);

        // Check if the uuid passed as args belongs to authenticated family
        if (!isMemberOfFamily(family, toBeUpdated)) {
            throw new AccessDeniedException("Unauthorized");
        }

        // Apply updates
        toBeUpdated.setName(update.name());
        toBeUpdated.setColor(update.color());
        toBeUpdated.setAvatarUrl(update.avatarUrl());
        toBeUpdated.setEmail(update.email());

        // Save
        return familyMemberRepository.save(toBeUpdated);
    }

    public void deleteFamilyMember(Family family, UUID familyMemberId) {
        // Check if familyMember with passed uuid exists
        FamilyMember toBeDeleted = findById(familyMemberId);

        // Check if the uuid passed as args belongs to authenticated family
        if (!isMemberOfFamily(family, toBeDeleted)) {
            throw new AccessDeniedException("Unauthorized");
        }

        familyMemberRepository.delete(toBeDeleted);
    }

    // -- Helper methods --

    private FamilyMember findById(UUID familyMemberId) {
        return familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new FamilyMemberNotFound(familyMemberId));
    }

    private boolean isMemberOfFamily(Family family, FamilyMember familyMember) {
        return familyMember.getFamily().getId()
                .equals(family.getId());
    }
}
