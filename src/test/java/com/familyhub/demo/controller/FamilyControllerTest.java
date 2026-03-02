package com.familyhub.demo.controller;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.config.JwtConfig;
import com.familyhub.demo.dto.FamilyResponse;
import com.familyhub.demo.security.WithMockFamily;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FamilyController.class)
class FamilyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FamilyService familyService;

    // Required by security filter chain
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private JwtConfig jwtConfig;

    @Test
    @WithMockFamily
    void getFamily_authenticated_returns200() throws Exception {
        FamilyResponse response = new FamilyResponse(
                TestDataFactory.FAMILY_ID, "Test Family", List.of(), LocalDateTime.now());
        when(familyService.findFamilyResponse(TestDataFactory.FAMILY_ID)).thenReturn(response);

        mockMvc.perform(get("/api/family"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Family"))
                .andExpect(jsonPath("$.message").value("Family Found"));
    }

    @Test
    void getFamily_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/family"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockFamily
    void updateFamily_returns200() throws Exception {
        FamilyResponse response = new FamilyResponse(
                TestDataFactory.FAMILY_ID, "Updated", List.of(), LocalDateTime.now());
        when(familyService.updateFamily(eq(TestDataFactory.FAMILY_ID), any())).thenReturn(response);

        mockMvc.perform(put("/api/family")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Updated", "username": "updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"));
    }

    @Test
    @WithMockFamily
    void deleteFamily_returns204() throws Exception {
        mockMvc.perform(delete("/api/family"))
                .andExpect(status().isNoContent());

        verify(familyService).deleteFamily(TestDataFactory.FAMILY_ID);
    }
}
