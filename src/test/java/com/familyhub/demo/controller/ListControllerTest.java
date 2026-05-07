package com.familyhub.demo.controller;

import com.familyhub.demo.config.SecurityConfig;
import com.familyhub.demo.dto.ClearCompletedResponse;
import com.familyhub.demo.dto.ListPreferencesResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.security.JwtAuthenticationEntryPoint;
import com.familyhub.demo.security.JwtAuthenticationFilter;
import com.familyhub.demo.security.WithMockFamily;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import com.familyhub.demo.service.ListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.familyhub.demo.TestDataFactory.LIST_ID;
import static com.familyhub.demo.TestDataFactory.LIST_ITEM_ID;
import static com.familyhub.demo.TestDataFactory.sampleListDetailResponse;
import static com.familyhub.demo.TestDataFactory.sampleListItemResponse;
import static com.familyhub.demo.TestDataFactory.sampleListSummaryResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
@ActiveProfiles("test")
class ListControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    FamilyService familyService;

    @MockitoBean
    ListService listService;

    @Test
    @WithMockFamily
    void getLists_returns200() throws Exception {
        given(listService.getLists(any(Family.class))).willReturn(List.of(sampleListSummaryResponse()));

        mockMvc.perform(get("/api/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Trader Joe's Run"))
                .andExpect(jsonPath("$.data[0].kind").value("grocery"));
    }

    @Test
    @WithMockFamily
    void createList_returns201WithLocationHeader() throws Exception {
        given(listService.createList(any(), any(Family.class))).willReturn(sampleListDetailResponse());

        mockMvc.perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Trader Joe's Run",
                                  "kind": "grocery"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/lists/" + LIST_ID))
                .andExpect(jsonPath("$.message").value("List created successfully"));
    }

    @Test
    @WithMockFamily
    void getList_returns200() throws Exception {
        given(listService.getList(eq(LIST_ID), any(Family.class))).willReturn(sampleListDetailResponse());

        mockMvc.perform(get("/api/lists/{id}", LIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(LIST_ID.toString()))
                .andExpect(jsonPath("$.data.categories[0].name").value("Produce"));
    }

    @Test
    @WithMockFamily
    void patchList_updatesDisplayModeAndOverride() throws Exception {
        given(listService.updateList(eq(LIST_ID), any(), any(Family.class))).willReturn(sampleListDetailResponse());

        mockMvc.perform(patch("/api/lists/{id}", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryDisplayMode": "flat",
                                  "showCompletedOverride": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("List updated successfully"));
    }

    @Test
    @WithMockFamily
    void createItem_returns201() throws Exception {
        given(listService.createItem(eq(LIST_ID), any(), any(Family.class))).willReturn(sampleListItemResponse());

        mockMvc.perform(post("/api/lists/{id}/items", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Bananas",
                                  "categoryId": "00000000-0000-0000-0000-000000000007"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/lists/" + LIST_ID + "/items/" + LIST_ITEM_ID))
                .andExpect(jsonPath("$.message").value("List item created successfully"));
    }

    @Test
    @WithMockFamily
    void patchItem_returns200() throws Exception {
        given(listService.updateItem(eq(LIST_ID), eq(LIST_ITEM_ID), any(), any(Family.class)))
                .willReturn(sampleListItemResponse());

        mockMvc.perform(patch("/api/lists/{listId}/items/{itemId}", LIST_ID, LIST_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Bananas",
                                  "completed": true,
                                  "categoryId": "00000000-0000-0000-0000-000000000007"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("List item updated successfully"));
    }

    @Test
    @WithMockFamily
    void deleteItem_returns204() throws Exception {
        willDoNothing().given(listService).deleteItem(eq(LIST_ID), eq(LIST_ITEM_ID), any(Family.class));

        mockMvc.perform(delete("/api/lists/{listId}/items/{itemId}", LIST_ID, LIST_ITEM_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockFamily
    void clearCompleted_returnsRemovedCount() throws Exception {
        given(listService.clearCompleted(eq(LIST_ID), any(Family.class)))
                .willReturn(new ClearCompletedResponse(2));

        mockMvc.perform(post("/api/lists/{id}/clear-completed", LIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removedCount").value(2))
                .andExpect(jsonPath("$.message").value("Completed items removed successfully"));
    }

    @Test
    @WithMockFamily
    void getPreferences_returns200() throws Exception {
        given(listService.getPreferences(any(Family.class))).willReturn(new ListPreferencesResponse(true));

        mockMvc.perform(get("/api/lists/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showCompletedByDefault").value(true));
    }

    @Test
    @WithMockFamily
    void patchPreferences_returns200() throws Exception {
        given(listService.updatePreferences(any(), any(Family.class))).willReturn(new ListPreferencesResponse(false));

        mockMvc.perform(patch("/api/lists/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "showCompletedByDefault": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showCompletedByDefault").value(false))
                .andExpect(jsonPath("$.message").value("List preferences updated successfully"));
    }

    @Test
    void getLists_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/lists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpStatus").value(401));
    }
}
