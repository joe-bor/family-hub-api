package com.familyhub.demo.service;

import com.familyhub.demo.dto.FamilyRequest;
import com.familyhub.demo.dto.FamilyResponse;
import com.familyhub.demo.exception.FamilyNotFoundException;
import com.familyhub.demo.mapper.FamilyMapper;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyService {
    private final FamilyRepository familyRepository;

    public List<Family> findAllFamilies() {
        return familyRepository.findAll();
    }

    public Family findByUsername(String username) {
        return familyRepository.findByUsername(username)
                .orElseThrow(() -> new FamilyNotFoundException(username));
    }

    @Transactional
    public FamilyResponse findFamilyResponse(UUID familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));
        return FamilyMapper.toDto(family);
    }

    public Family findFamilyById(UUID familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));
    }

    @Transactional
    public FamilyResponse updateFamily(UUID id, FamilyRequest family) {
        Family toBeUpdated = findFamilyById(id);

        if (family.name() != null) {
            toBeUpdated.setName(family.name());
        }

        if (family.username() != null) {
            toBeUpdated.setUsername(family.username());
        }

        return FamilyMapper.toDto(familyRepository.save(toBeUpdated));
    }

    public void deleteFamily(UUID id) {
        Family toBeDeleted = findFamilyById(id);
        familyRepository.delete(toBeDeleted);
    }
}
