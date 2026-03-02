package com.familyhub.demo.controller;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.config.JwtConfig;
import com.familyhub.demo.dto.FamilyMemberResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyColor;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.security.WithMockFamily;
import com.familyhub.demo.service.FamilyMemberService;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FamilyMemberController.class)
class FamilyMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FamilyMemberService familyMemberService;

    // Required by security filter chain
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private FamilyService familyService;
    @MockitoBean
    private JwtConfig jwtConfig;

    @Test
    @WithMockFamily
    void getMembers_returns200() throws Exception {
        FamilyMemberResponse memberResponse = TestDataFactory.createFamilyMemberResponse();
        when(familyMemberService.findAllMembers(any(Family.class))).thenReturn(List.of(memberResponse));

        mockMvc.perform(get("/api/family/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("John"))
                .andExpect(jsonPath("$.message").value("Get members"));
    }

    @Test
    @WithMockFamily
    void getMemberById_returns200() throws Exception {
        FamilyMemberResponse memberResponse = TestDataFactory.createFamilyMemberResponse();
        when(familyMemberService.findById(any(Family.class), eq(TestDataFactory.MEMBER_ID)))
                .thenReturn(memberResponse);

        mockMvc.perform(get("/api/family/members/{id}", TestDataFactory.MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("John"));
    }

    @Test
    @WithMockFamily
    void addMember_returns201WithLocation() throws Exception {
        FamilyMember member = TestDataFactory.createFamilyMember(TestDataFactory.createFamily());
        when(familyMemberService.addFamilyMember(any(Family.class), any())).thenReturn(member);

        mockMvc.perform(post("/api/family/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "John", "color": "coral"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/family/members/" + member.getId()))
                .andExpect(jsonPath("$.message").value("Family Member Added"));
    }

    @Test
    @WithMockFamily
    void updateMember_returns200() throws Exception {
        FamilyMember member = TestDataFactory.createFamilyMember(TestDataFactory.createFamily());
        when(familyMemberService.updateFamilyMember(any(Family.class), eq(TestDataFactory.MEMBER_ID), any()))
                .thenReturn(member);

        mockMvc.perform(put("/api/family/members/{id}", TestDataFactory.MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Jane", "color": "teal"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Family Member Updated"));
    }

    @Test
    @WithMockFamily
    void deleteMember_returns204() throws Exception {
        mockMvc.perform(delete("/api/family/members/{id}", TestDataFactory.MEMBER_ID))
                .andExpect(status().isNoContent());

        verify(familyMemberService).deleteFamilyMember(any(Family.class), eq(TestDataFactory.MEMBER_ID));
    }

    @Test
    void getMembers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/family/members"))
                .andExpect(status().isUnauthorized());
    }
}
