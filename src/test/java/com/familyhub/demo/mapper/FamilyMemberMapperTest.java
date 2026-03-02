package com.familyhub.demo.mapper;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.dto.FamilyMemberRequest;
import com.familyhub.demo.dto.FamilyMemberResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyColor;
import com.familyhub.demo.model.FamilyMember;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyMemberMapperTest {

    @Test
    void toEntity_mapsAllFields() {
        Family family = TestDataFactory.createFamily();
        FamilyMemberRequest request = new FamilyMemberRequest("Alice", FamilyColor.PURPLE, "alice@test.com", "https://avatar.url");

        FamilyMember entity = FamilyMemberMapper.toEntity(request, family);

        assertThat(entity.getName()).isEqualTo("Alice");
        assertThat(entity.getColor()).isEqualTo(FamilyColor.PURPLE);
        assertThat(entity.getEmail()).isEqualTo("alice@test.com");
        assertThat(entity.getAvatarUrl()).isEqualTo("https://avatar.url");
        assertThat(entity.getFamily()).isEqualTo(family);
    }

    @Test
    void toDto_mapsAllFields() {
        Family family = TestDataFactory.createFamily();
        FamilyMember member = TestDataFactory.createFamilyMember(family);

        FamilyMemberResponse dto = FamilyMemberMapper.toDto(member);

        assertThat(dto.id()).isEqualTo(member.getId());
        assertThat(dto.name()).isEqualTo(member.getName());
        assertThat(dto.color()).isEqualTo(member.getColor());
        assertThat(dto.email()).isEqualTo(member.getEmail());
        assertThat(dto.avatarUrl()).isEqualTo(member.getAvatarUrl());
    }

    @Test
    void toEntity_toDto_roundTrip() {
        Family family = TestDataFactory.createFamily();
        FamilyMemberRequest request = new FamilyMemberRequest("Bob", FamilyColor.TEAL, "bob@test.com", null);

        FamilyMember entity = FamilyMemberMapper.toEntity(request, family);
        FamilyMemberResponse dto = FamilyMemberMapper.toDto(entity);

        assertThat(dto.name()).isEqualTo(request.name());
        assertThat(dto.color()).isEqualTo(request.color());
        assertThat(dto.email()).isEqualTo(request.email());
        assertThat(dto.avatarUrl()).isEqualTo(request.avatarUrl());
    }
}
