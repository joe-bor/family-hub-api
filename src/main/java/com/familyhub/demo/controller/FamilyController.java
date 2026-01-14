package com.familyhub.demo.controller;

import com.familyhub.demo.model.Family;
import com.familyhub.demo.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FamilyController {
    private final FamilyService familyService;

    @GetMapping("/family")
    ResponseEntity<List<Family>> findAllFamilies() {
        List<Family> allFamilies = familyService.findAllFamilies();

        return allFamilies.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.OK).body(allFamilies);
    }

    @GetMapping("/family/{id}")
    ResponseEntity<Family> findFamilyById(@PathVariable UUID id) {
        Family familyById = familyService.findFamilyById(id);
        return ResponseEntity.ok(familyById);
    }

    @PostMapping("/family")
    ResponseEntity<Family> createFamily(@RequestBody Family tobeCreated) {
        Family family = familyService.createFamily(tobeCreated);
        URI location = URI.create("/api/family" + family.getId());
        return ResponseEntity.created(location).body(family);
    }

    @PutMapping("/family/{id}")
    ResponseEntity<Family> updateFamily(@PathVariable UUID id, @RequestBody Family family) {
        Family updatedFamily = familyService.updateFamily(id, family);
        return ResponseEntity.ok(updatedFamily);
    }

    @DeleteMapping("/family/{id}")
    ResponseEntity<Void> deleteFamily(@PathVariable UUID id) {
        familyService.deleteFamily(id);
        return ResponseEntity.noContent().build();
    }
}
