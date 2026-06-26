package com.familyhub.demo.integration;

import com.familyhub.demo.config.TestcontainersConfig;
import com.familyhub.demo.dto.CreateListCategoryRequest;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.ListKind;
import com.familyhub.demo.repository.FamilyRepository;
import com.familyhub.demo.repository.ListCategoryCatalogScopeRepository;
import com.familyhub.demo.repository.ListCategoryRepository;
import com.familyhub.demo.service.ListCategoryService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private ListCategoryRepository listCategoryRepository;

    @Autowired
    private ListCategoryCatalogScopeRepository scopeRepository;

    @Autowired
    private ListCategoryService listCategoryService;

    private String uniqueUsername() {
        return "lists" + System.nanoTime();
    }

    private String registerAndGetToken(String username) throws Exception {
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

        return JsonPath.read(authBody, "$.data.token");
    }

    // -------------------------------------------------------------------------
    // Original end-to-end round-trip test
    // -------------------------------------------------------------------------

    @Test
    void listsFlow_roundTripsThroughAuthenticatedApi() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);

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

    // -------------------------------------------------------------------------
    // Registration seeds catalog scopes and starter categories
    // -------------------------------------------------------------------------

    @Test
    void registration_createsCatalogScopesForAllKinds() throws Exception {
        String username = uniqueUsername();
        registerAndGetToken(username);

        Family family = familyRepository.findByUsername(username).orElseThrow();
        long familyScopeCount = scopeRepository.findAll().stream()
                .filter(s -> s.getFamily().getId().equals(family.getId()))
                .count();
        assertThat(familyScopeCount).isEqualTo(3L);
    }

    @Test
    void registration_scopeKindsAreGroceryTodoGeneral() throws Exception {
        String username = uniqueUsername();
        registerAndGetToken(username);

        Family family = familyRepository.findByUsername(username).orElseThrow();
        var kinds = scopeRepository.findAll().stream()
                .filter(s -> s.getFamily().getId().equals(family.getId()))
                .map(s -> s.getKind())
                .toList();
        assertThat(kinds).containsExactlyInAnyOrder(ListKind.GROCERY, ListKind.TODO, ListKind.GENERAL);
    }

    @Test
    void registration_createsGroceryAndTodoStarterCategories_noGeneralStarters() throws Exception {
        String username = uniqueUsername();
        registerAndGetToken(username);

        Family family = familyRepository.findByUsername(username).orElseThrow();

        long groceryCount = listCategoryRepository.countByFamilyAndKind(family, ListKind.GROCERY);
        long todoCount = listCategoryRepository.countByFamilyAndKind(family, ListKind.TODO);
        long generalCount = listCategoryRepository.countByFamilyAndKind(family, ListKind.GENERAL);

        assertThat(groceryCount).isEqualTo(5L);  // Produce, Dairy, Pantry, Frozen, Household
        assertThat(todoCount).isEqualTo(3L);      // Urgent, Soon, Later
        assertThat(generalCount).isEqualTo(0L);   // No General starters
    }

    @Test
    void registration_isOneTime_loginDoesNotReseed() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);
        Family family = familyRepository.findByUsername(username).orElseThrow();

        // Get a grocery list so we can see the seeded categories
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Test List", "kind": "grocery"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // Get the first category ID
        String detailBody = mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String catId = JsonPath.read(detailBody, "$.data.categories[0].id");
        String originalName = JsonPath.read(detailBody, "$.data.categories[0].name");

        // Rename via service (category rename via service; no controller yet)
        listCategoryService.rename(
                java.util.UUID.fromString(catId),
                new com.familyhub.demo.dto.RenameListCategoryRequest("Fresh Produce"),
                family
        );

        // Login again (triggers no reseed)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "TestPassword123!"}
                                """.formatted(username)))
                .andExpect(status().isOk());

        // Category still has the renamed name — no reseed happened
        mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].name").value("Fresh Produce"));
    }

    // -------------------------------------------------------------------------
    // Grocery/To-do list creation display mode — based on catalog state
    // -------------------------------------------------------------------------

    @Test
    void createGroceryList_withSeededCategories_isGrouped() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);

        // Registration seeded Grocery categories → new grocery list should be GROUPED
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "My Groceries", "kind": "grocery"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(listBody, "$.data.categoryDisplayMode")).isEqualTo("grouped");
    }

    @Test
    void createGeneralList_alwaysFlat() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);

        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Movies", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(listBody, "$.data.categoryDisplayMode")).isEqualTo("flat");
    }

    // -------------------------------------------------------------------------
    // updateList GROUPED — empty-catalog 409 for all kinds
    // -------------------------------------------------------------------------

    @Test
    void updateList_groupedPatch_emptyCatalogGeneral_returns409() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);

        // Create a general list (no categories exist for GENERAL)
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Notes", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // GROUPED PATCH with empty catalog → 409
        mockMvc.perform(patch("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryDisplayMode": "grouped"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void updateList_groupedPatch_generalWithCategories_succeeds() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);
        Family family = familyRepository.findByUsername(username).orElseThrow();

        // Create a General list
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Notes", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // Add a category to GENERAL via service
        listCategoryService.create(new CreateListCategoryRequest(ListKind.GENERAL, "Documents"), family);

        // Now GROUPED PATCH should succeed
        mockMvc.perform(patch("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryDisplayMode": "grouped"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryDisplayMode").value("grouped"));
    }

    // -------------------------------------------------------------------------
    // General item create/update accepts matching-kind category
    // -------------------------------------------------------------------------

    @Test
    void createItem_generalList_withGeneralCategory_succeeds() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);
        Family family = familyRepository.findByUsername(username).orElseThrow();

        // Create General list
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Movies", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // Add General category via service
        var catEntry = listCategoryService.create(
                new CreateListCategoryRequest(ListKind.GENERAL, "Action"), family);
        String catId = catEntry.id().toString();

        // Create item with category → should succeed
        mockMvc.perform(post("/api/lists/{id}/items", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Die Hard", "categoryId": "%s"}
                                """.formatted(catId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.text").value("Die Hard"));
    }

    @Test
    void createItem_generalList_withGroceryCategory_returns400() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);

        // Get a Grocery category from the seeded ones
        String groceryListBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Groceries", "kind": "grocery"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String groceryListId = JsonPath.read(groceryListBody, "$.data.id");

        String detailBody = mockMvc.perform(get("/api/lists/{id}", groceryListId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String groceryCatId = JsonPath.read(detailBody, "$.data.categories[0].id");

        // Create General list
        String generalListBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Notes", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String generalListId = JsonPath.read(generalListBody, "$.data.id");

        // Try to assign a Grocery category to a General list item → 400
        mockMvc.perform(post("/api/lists/{id}/items", generalListId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Apples", "categoryId": "%s"}
                                """.formatted(groceryCatId)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // List detail exposes categories for ALL kinds, no seeded field
    // -------------------------------------------------------------------------

    @Test
    void listDetail_generalKind_exposesCategoriesWhenPresent() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);
        Family family = familyRepository.findByUsername(username).orElseThrow();

        // Create General list
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Notes", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // Add General category via service
        listCategoryService.create(new CreateListCategoryRequest(ListKind.GENERAL, "Documents"), family);

        // List detail should include the category
        mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].name").value("Documents"))
                .andExpect(jsonPath("$.data.categories[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data.categories[0].sortOrder").isNumber());
    }

    @Test
    void listDetail_categories_doNotHaveSeededField() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);

        // Grocery list with seeded categories from registration
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "My Groceries", "kind": "grocery"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // seeded field must NOT appear in the response
        mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0]").exists())
                .andExpect(jsonPath("$.data.categories[0].seeded").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // Sequential race outcomes: delete-then-operation
    // -------------------------------------------------------------------------

    @Test
    void deleteLastCategory_thenGroupedPatch_returns409_andListIsFlat() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);
        Family family = familyRepository.findByUsername(username).orElseThrow();

        // Create a General list
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Notes", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // Add one category and switch list to GROUPED
        var catEntry = listCategoryService.create(
                new CreateListCategoryRequest(ListKind.GENERAL, "Documents"), family);

        mockMvc.perform(patch("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryDisplayMode": "grouped"}
                                """))
                .andExpect(status().isOk());

        // Delete the only category (service flattens grouped lists)
        listCategoryService.delete(catEntry.id(), family);

        // GROUPED PATCH must return 409 (empty catalog)
        mockMvc.perform(patch("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryDisplayMode": "grouped"}
                                """))
                .andExpect(status().isConflict());

        // List must now be flat (flattened by the delete)
        mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryDisplayMode").value("flat"));
    }

    @Test
    void deleteLastCategory_thenItemAssignment_returns404_andItemUnchanged() throws Exception {
        String username = uniqueUsername();
        String token = registerAndGetToken(username);
        Family family = familyRepository.findByUsername(username).orElseThrow();

        // Create a General list
        String listBody = mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Notes", "kind": "general"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String listId = JsonPath.read(listBody, "$.data.id");

        // Add a category
        var catEntry = listCategoryService.create(
                new CreateListCategoryRequest(ListKind.GENERAL, "Documents"), family);
        String catId = catEntry.id().toString();

        // Add an item (no category)
        String itemBody = mockMvc.perform(post("/api/lists/{id}/items", listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Meeting notes"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(itemBody, "$.data.id");

        // Delete the category
        listCategoryService.delete(catEntry.id(), family);

        // Try to assign the now-deleted category to the item → 404
        mockMvc.perform(patch("/api/lists/{listId}/items/{itemId}", listId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Meeting notes", "completed": false, "categoryId": "%s"}
                                """.formatted(catId)))
                .andExpect(status().isNotFound());

        // Item should be unchanged (null category)
        mockMvc.perform(get("/api/lists/{id}", listId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].categoryId").value(nullValue()));
    }
}
