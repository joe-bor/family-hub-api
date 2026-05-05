package com.familyhub.demo.service;

import com.familyhub.demo.dto.ChoreResponse;
import com.familyhub.demo.dto.CreateChoreRequest;
import com.familyhub.demo.dto.UpdateChoreRequest;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.mapper.ChoreMapper;
import com.familyhub.demo.model.Chore;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.repository.ChoreRepository;
import com.familyhub.demo.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChoreService {
    private final ChoreRepository choreRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public List<ChoreResponse> getChores(Family family) {
        return choreRepository.findByFamilyWithAssignee(family)
                .stream()
                .map(ChoreMapper::toDto)
                .toList();
    }

    @Transactional
    public ChoreResponse createChore(CreateChoreRequest request, Family family) {
        FamilyMember assignedToMember = resolveFamilyMember(family, request.assignedToMemberId());

        Chore chore = new Chore();
        chore.setFamily(family);
        chore.setAssignedToMember(assignedToMember);
        chore.setTitle(request.title().trim());
        chore.setDueDate(request.dueDate());
        chore.setCompleted(false);
        chore.setCompletedAt(null);

        return ChoreMapper.toDto(choreRepository.save(chore));
    }

    @Transactional
    public ChoreResponse updateChore(UUID id, UpdateChoreRequest request, Family family) {
        Chore chore = choreRepository.findByFamilyAndId(family, id)
                .orElseThrow(() -> new ResourceNotFoundException("Chore", id));

        boolean completed = Boolean.TRUE.equals(request.completed());
        chore.setCompleted(completed);
        if (completed) {
            if (chore.getCompletedAt() == null) {
                chore.setCompletedAt(LocalDateTime.now());
            }
        } else {
            chore.setCompletedAt(null);
        }

        return ChoreMapper.toDto(choreRepository.save(chore));
    }

    @Transactional
    public void deleteChore(UUID id, Family family) {
        Chore chore = choreRepository.findByFamilyAndId(family, id)
                .orElseThrow(() -> new ResourceNotFoundException("Chore", id));

        choreRepository.delete(chore);
    }

    private FamilyMember resolveFamilyMember(Family family, UUID memberId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Family Member", memberId));

        if (!member.getFamily().getId().equals(family.getId())) {
            throw new AccessDeniedException("Unauthorized");
        }

        return member;
    }
}
