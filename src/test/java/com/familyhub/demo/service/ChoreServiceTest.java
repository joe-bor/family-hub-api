package com.familyhub.demo.service;

import com.familyhub.demo.dto.ChoreResponse;
import com.familyhub.demo.dto.UpdateChoreRequest;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.model.Chore;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.repository.ChoreRepository;
import com.familyhub.demo.repository.FamilyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static com.familyhub.demo.TestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChoreServiceTest {

    @Mock
    private ChoreRepository choreRepository;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @InjectMocks
    private ChoreService choreService;

    private Family family;
    private FamilyMember member;
    private Chore chore;

    @BeforeEach
    void setUp() {
        family = createFamily();
        member = createFamilyMember(family);
        chore = createChore(family, member);
    }

    @Test
    void getChores_returnsFamilyScopedList() {
        when(choreRepository.findByFamilyWithAssignee(family)).thenReturn(List.of(chore));

        List<ChoreResponse> result = choreService.getChores(family);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(CHORE_ID);
        assertThat(result.getFirst().assignedToMemberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void createChore_success_returnsSavedChore() {
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(choreRepository.save(any(Chore.class))).thenReturn(chore);

        ChoreResponse result = choreService.createChore(createChoreRequest(MEMBER_ID), family);

        assertThat(result.id()).isEqualTo(CHORE_ID);
        assertThat(result.title()).isEqualTo("🗑️ Take out trash");
        verify(choreRepository).save(any(Chore.class));
    }

    @Test
    void createChore_memberFromWrongFamily_throwsAccessDenied() {
        Family otherFamily = createOtherFamily();
        FamilyMember wrongMember = createFamilyMember(otherFamily);

        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(wrongMember));

        assertThatThrownBy(() -> choreService.createChore(createChoreRequest(MEMBER_ID), family))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateChore_completedTrue_setsCompletedAt() {
        when(choreRepository.findByFamilyAndId(family, CHORE_ID)).thenReturn(Optional.of(chore));
        when(choreRepository.save(chore)).thenReturn(chore);

        choreService.updateChore(CHORE_ID, new UpdateChoreRequest(true), family);

        assertThat(chore.isCompleted()).isTrue();
        assertThat(chore.getCompletedAt()).isNotNull();
    }

    @Test
    void updateChore_completedFalse_clearsCompletedAt() {
        Chore completedChore = createCompletedChore(family, member);

        when(choreRepository.findByFamilyAndId(family, CHORE_ID)).thenReturn(Optional.of(completedChore));
        when(choreRepository.save(completedChore)).thenReturn(completedChore);

        choreService.updateChore(CHORE_ID, new UpdateChoreRequest(false), family);

        assertThat(completedChore.isCompleted()).isFalse();
        assertThat(completedChore.getCompletedAt()).isNull();
    }

    @Test
    void updateChore_notFound_throwsResourceNotFound() {
        when(choreRepository.findByFamilyAndId(family, CHORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> choreService.updateChore(CHORE_ID, new UpdateChoreRequest(true), family))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteChore_success() {
        when(choreRepository.findByFamilyAndId(family, CHORE_ID)).thenReturn(Optional.of(chore));

        choreService.deleteChore(CHORE_ID, family);

        verify(choreRepository).delete(chore);
    }
}
