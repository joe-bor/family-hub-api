package com.familyhub.demo.service;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.dto.FamilyMemberRequest;
import com.familyhub.demo.dto.FamilyMemberResponse;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyColor;
import com.familyhub.demo.model.FamilyMember;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyMemberServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @InjectMocks
    private FamilyMemberService familyMemberService;

    private Family family;
    private FamilyMember member;

    @BeforeEach
    void setUp() {
        family = TestDataFactory.createFamily();
        member = TestDataFactory.createFamilyMember(family);
    }

    @Test
    void findAllMembers_returnsList() {
        when(familyMemberRepository.findByFamily(family)).thenReturn(List.of(member));

        List<FamilyMemberResponse> result = familyMemberService.findAllMembers(family);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("John");
    }

    @Test
    void findById_success() {
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        FamilyMemberResponse result = familyMemberService.findById(family, member.getId());

        assertThat(result.id()).isEqualTo(member.getId());
        assertThat(result.name()).isEqualTo("John");
    }

    @Test
    void findById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(familyMemberRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyMemberService.findById(family, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_wrongFamily_throws() {
        Family otherFamily = TestDataFactory.createOtherFamily();
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyMemberService.findById(otherFamily, member.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addFamilyMember_success() {
        FamilyMemberRequest request = TestDataFactory.createFamilyMemberRequest();
        when(familyMemberRepository.save(any(FamilyMember.class))).thenReturn(member);

        FamilyMember result = familyMemberService.addFamilyMember(family, request);

        assertThat(result.getName()).isEqualTo("John");
        verify(familyMemberRepository).save(any(FamilyMember.class));
    }

    @Test
    void updateFamilyMember_success() {
        FamilyMemberRequest request = new FamilyMemberRequest("Jane", FamilyColor.TEAL, "jane@test.com", null);
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(familyMemberRepository.save(any(FamilyMember.class))).thenReturn(member);

        FamilyMember result = familyMemberService.updateFamilyMember(family, member.getId(), request);

        assertThat(member.getName()).isEqualTo("Jane");
        assertThat(member.getColor()).isEqualTo(FamilyColor.TEAL);
    }

    @Test
    void updateFamilyMember_wrongFamily_throws() {
        Family otherFamily = TestDataFactory.createOtherFamily();
        FamilyMemberRequest request = TestDataFactory.createFamilyMemberRequest();
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyMemberService.updateFamilyMember(otherFamily, member.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteFamilyMember_success() {
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        familyMemberService.deleteFamilyMember(family, member.getId());

        verify(familyMemberRepository).delete(member);
    }

    @Test
    void deleteFamilyMember_wrongFamily_throws() {
        Family otherFamily = TestDataFactory.createOtherFamily();
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyMemberService.deleteFamilyMember(otherFamily, member.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
