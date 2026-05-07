package com.familyhub.demo.integration;

import com.familyhub.demo.config.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String uniqueUsername() {
        return "lists" + System.nanoTime();
    }

    @Test
    void listsFlow_roundTripsThroughAuthenticatedApi() throws Exception {
        String username = uniqueUsername();

        String authBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "TestPassword123!",
                                  "familyName": "Lists Family",
                                  "members": [
                                    { "name": "Alice", "color": "coral" }
                                  ]
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(authBody, "$.data.token");

        mockMvc.perform(get("/api/lists/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showCompletedByDefault").value(true));

        mockMvc.perform(patch("/api/lists/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "showCompletedByDefault": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showCompletedByDefault").value(false));

        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Trader Joe's Run",
                                  "kind": "grocery"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("grocery"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String listId = JsonPath.read(listBody, "$.data.id");

        mockMvc.perform(get("/api/lists")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(listId))
                .andExpect(jsonPath("$.data[0].totalItems").value(0))
                .andExpect(jsonPath("$.data[0].completedItems").value(0));

        mockMvc.perform(patch("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryDisplayMode": "flat",
                                  "showCompletedOverride": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryDisplayMode").value("flat"))
                .andExpect(jsonPath("$.data.showCompletedOverride").value(true));

        mockMvc.perform(patch("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryDisplayMode": "flat",
                                  "showCompletedOverride": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showCompletedOverride").value(nullValue()));

        String detailBody = mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].name").value("Produce"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String produceCategoryId = JsonPath.read(detailBody, "$.data.categories[0].id");

        String bananasBody = mockMvc.perform(post("/api/lists/{id}/items", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Bananas",
                                  "categoryId": "%s"
                                }
                                """.formatted(produceCategoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.text").value("Bananas"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String bananasItemId = JsonPath.read(bananasBody, "$.data.id");

        String yogurtBody = mockMvc.perform(post("/api/lists/{id}/items", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Greek yogurt"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.text").value("Greek yogurt"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String yogurtItemId = JsonPath.read(yogurtBody, "$.data.id");

        mockMvc.perform(patch("/api/lists/{listId}/items/{itemId}", listId, bananasItemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Bananas",
                                  "completed": true,
                                  "categoryId": "%s"
                                }
                                """.formatted(produceCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        mockMvc.perform(delete("/api/lists/{listId}/items/{itemId}", listId, yogurtItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/lists/{id}/clear-completed", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removedCount").value(1));

        mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
