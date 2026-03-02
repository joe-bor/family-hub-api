package com.familyhub.demo.service;

import com.familyhub.demo.TestDataFactory;
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

import java.util.Optional;
import java.util.UUID;

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
        family = TestDataFactory.createFamily();
    }

    @Test
    void findFamilyResponse_success() {
        when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));

        FamilyResponse response = familyService.findFamilyResponse(family.getId());

        assertThat(response.id()).isEqualTo(family.getId());
        assertThat(response.name()).isEqualTo(family.getName());
    }

    @Test
    void findFamilyResponse_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(familyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.findFamilyResponse(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findFamilyById_success() {
        when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));

        Family result = familyService.findFamilyById(family.getId());

        assertThat(result).isEqualTo(family);
    }

    @Test
    void findFamilyById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(familyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.findFamilyById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateFamily_partialFields_onlyName() {
        FamilyRequest request = new FamilyRequest("New Name", null);
        when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));
        when(familyRepository.save(any(Family.class))).thenReturn(family);

        FamilyResponse response = familyService.updateFamily(family.getId(), request);

        assertThat(family.getName()).isEqualTo("New Name");
        assertThat(family.getUsername()).isEqualTo("testfamily");
    }

    @Test
    void updateFamily_partialFields_onlyUsername() {
        FamilyRequest request = new FamilyRequest(null, "newusername");
        when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));
        when(familyRepository.save(any(Family.class))).thenReturn(family);

        familyService.updateFamily(family.getId(), request);

        assertThat(family.getUsername()).isEqualTo("newusername");
        assertThat(family.getName()).isEqualTo("Test Family");
    }

    @Test
    void updateFamily_allFields() {
        FamilyRequest request = TestDataFactory.createFamilyRequest();
        when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));
        when(familyRepository.save(any(Family.class))).thenReturn(family);

        familyService.updateFamily(family.getId(), request);

        assertThat(family.getName()).isEqualTo("Updated Family");
        assertThat(family.getUsername()).isEqualTo("updatedfamily");
    }

    @Test
    void deleteFamily_success() {
        when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));

        familyService.deleteFamily(family.getId());

        verify(familyRepository).delete(family);
    }

    @Test
    void deleteFamily_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(familyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.deleteFamily(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
