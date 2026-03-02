package com.familyhub.demo.mapper;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.dto.FamilyResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyMapperTest {

    @Test
    void toDto_mapsAllFields() {
        Family family = TestDataFactory.createFamily();

        FamilyResponse dto = FamilyMapper.toDto(family);

        assertThat(dto.id()).isEqualTo(family.getId());
        assertThat(dto.name()).isEqualTo(family.getName());
        assertThat(dto.createdAt()).isEqualTo(family.getCreatedAt());
        assertThat(dto.members()).isEmpty();
    }

    @Test
    void toDto_mapsNestedMembers() {
        Family family = TestDataFactory.createFamily();
        FamilyMember member = TestDataFactory.createFamilyMember(family);
        family.setFamilyMembers(List.of(member));

        FamilyResponse dto = FamilyMapper.toDto(family);

        assertThat(dto.members()).hasSize(1);
        assertThat(dto.members().getFirst().name()).isEqualTo("John");
        assertThat(dto.members().getFirst().id()).isEqualTo(member.getId());
    }
}
