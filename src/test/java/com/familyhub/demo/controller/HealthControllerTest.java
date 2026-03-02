package com.familyhub.demo.controller;

import com.familyhub.demo.config.JwtConfig;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Required by security filter chain
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private FamilyService familyService;
    @MockitoBean
    private JwtConfig jwtConfig;

    @Test
    void health_returnsUp_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("family-hub-api"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }
}
