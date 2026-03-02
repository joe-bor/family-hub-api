package com.familyhub.demo.repository;

import com.familyhub.demo.config.TestcontainersConfig;
import com.familyhub.demo.model.Family;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FamilyRepositoryTest {

    @Autowired
    private FamilyRepository familyRepository;

    private Family createAndSaveFamily(String username) {
        Family family = new Family();
        family.setName("Test Family");
        family.setUsername(username);
        family.setPasswordHash("encoded");
        family.setFamilyMembers(new ArrayList<>());
        return familyRepository.save(family);
    }

    @Test
    void existsByUsername_returnsTrue() {
        createAndSaveFamily("testfamily");

        assertThat(familyRepository.existsByUsername("testfamily")).isTrue();
    }

    @Test
    void existsByUsername_returnsFalse() {
        assertThat(familyRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    void findByUsername_present() {
        createAndSaveFamily("testfamily");

        Optional<Family> result = familyRepository.findByUsername("testfamily");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Family");
    }

    @Test
    void findByUsername_empty() {
        Optional<Family> result = familyRepository.findByUsername("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void uniqueUsernameConstraint_violation() {
        createAndSaveFamily("duplicate");

        Family duplicate = new Family();
        duplicate.setName("Another Family");
        duplicate.setUsername("duplicate");
        duplicate.setPasswordHash("encoded");
        duplicate.setFamilyMembers(new ArrayList<>());

        assertThatThrownBy(() -> familyRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
