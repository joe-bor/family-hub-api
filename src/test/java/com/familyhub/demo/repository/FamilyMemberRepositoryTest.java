package com.familyhub.demo.repository;

import com.familyhub.demo.config.TestcontainersConfig;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyColor;
import com.familyhub.demo.model.FamilyMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FamilyMemberRepositoryTest {

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    private Family createAndSaveFamily(String username) {
        Family family = new Family();
        family.setName("Test Family");
        family.setUsername(username);
        family.setPasswordHash("encoded");
        family.setFamilyMembers(new ArrayList<>());
        return familyRepository.save(family);
    }

    private FamilyMember createAndSaveMember(Family family, String name, FamilyColor color) {
        FamilyMember member = new FamilyMember();
        member.setFamily(family);
        member.setName(name);
        member.setColor(color);
        return familyMemberRepository.save(member);
    }

    @Test
    void findByFamily_returnsCorrectMembers() {
        Family family1 = createAndSaveFamily("family1");
        Family family2 = createAndSaveFamily("family2");
        createAndSaveMember(family1, "Alice", FamilyColor.CORAL);
        createAndSaveMember(family1, "Bob", FamilyColor.TEAL);
        createAndSaveMember(family2, "Charlie", FamilyColor.GREEN);

        List<FamilyMember> result = familyMemberRepository.findByFamily(family1);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FamilyMember::getName).containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void cascadeDelete_whenFamilyDeleted() {
        Family family = createAndSaveFamily("cascade_test");
        FamilyMember member = createAndSaveMember(family, "Alice", FamilyColor.CORAL);

        // Add member to family's collection so cascade is aware
        family.getFamilyMembers().add(member);
        familyRepository.save(family);

        assertThat(familyMemberRepository.findByFamily(family)).hasSize(1);

        familyRepository.delete(family);
        familyRepository.flush();

        assertThat(familyMemberRepository.findAll()).isEmpty();
    }
}
