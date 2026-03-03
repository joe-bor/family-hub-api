package com.familyhub.demo.service;

import com.familyhub.demo.dto.FamilyRequest;
import com.familyhub.demo.dto.FamilyResponse;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.repository.FamilyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyRepository familyRepository;

    @InjectMocks
    private FamilyService familyService;

    private Family family;

    @BeforeEach
    void setUp() {
        family = createFamily();
        family.setFamilyMembers(List.of());
    }

    @Test
    void updateFamily_nameOnly_updatesName() {
        FamilyRequest request = new FamilyRequest("New Name", null);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));
        when(familyRepository.save(any(Family.class))).thenReturn(family);

        FamilyResponse result = familyService.updateFamily(FAMILY_ID, request);

        assertThat(result).isNotNull();
        assertThat(family.getName()).isEqualTo("New Name");
        assertThat(family.getUsername()).isEqualTo("testfamily");
    }

    @Test
    void updateFamily_usernameOnly_updatesUsername() {
        FamilyRequest request = new FamilyRequest(null, "newusername");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));
        when(familyRepository.save(any(Family.class))).thenReturn(family);

        familyService.updateFamily(FAMILY_ID, request);

        assertThat(family.getUsername()).isEqualTo("newusername");
        assertThat(family.getName()).isEqualTo("Test Family");
    }

    @Test
    void updateFamily_fullUpdate_updatesBoth() {
        FamilyRequest request = new FamilyRequest("New Name", "newusername");
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));
        when(familyRepository.save(any(Family.class))).thenReturn(family);

        familyService.updateFamily(FAMILY_ID, request);

        assertThat(family.getName()).isEqualTo("New Name");
        assertThat(family.getUsername()).isEqualTo("newusername");
    }

    @Test
    void deleteFamily_success() {
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family));

        familyService.deleteFamily(FAMILY_ID);

        verify(familyRepository).delete(family);
    }

    @Test
    void deleteFamily_notFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(familyRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.deleteFamily(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
