package com.familyhub.demo.service;

import com.familyhub.demo.model.Family;
import com.familyhub.demo.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyService {
    private final FamilyRepository familyRepository;

    public List<Family> findAllFamilies() {
        return familyRepository.findAll();
    }

    public Family findFamilyById(UUID familyId) {
         return familyRepository.findById(familyId)
                 .orElseThrow(() -> new RuntimeException(familyId + " does not exist"));
    }

    public Family createFamily(Family family) {
        return familyRepository.save(family);
    }

    public Family updateFamily(UUID id, Family family) {
        Family toBeUpdated = findFamilyById(id);

        if (family.getName() != null) {
            toBeUpdated.setName(family.getName());
        }

        if (family.getUsername() != null) {
            toBeUpdated.setUsername(family.getUsername());
        }

        return familyRepository.save(toBeUpdated);
    }

    public void deleteFamily(UUID id) {
        Family toBeDeleted = findFamilyById(id);
        familyRepository.delete(toBeDeleted);
    }
}
