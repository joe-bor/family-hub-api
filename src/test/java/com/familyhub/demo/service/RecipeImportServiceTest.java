package com.familyhub.demo.service;

import com.familyhub.demo.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.MockitoAnnotations.openMocks;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RecipeImportServiceTest {

    @Mock
    private HttpClient httpClient;

    private RecipeImportService recipeImportService;

    @BeforeEach
    void setUp() {
        openMocks(this);
        recipeImportService = new RecipeImportService(new ObjectMapper(), httpClient);
    }

    @Test
    void parseHtml_extractsJsonLdRecipeFields() {
        String html = """
                <html>
                  <head>
                    <script type="application/ld+json">
                    {
                      "@context":"https://schema.org",
                      "@type":"Recipe",
                      "name":"Weeknight Tacos",
                      "image":"https://cdn.example.com/tacos.jpg",
                      "recipeIngredient":["1 lb beef","8 tortillas"],
                      "recipeInstructions":["Brown beef","Serve in tortillas"]
                    }
                    </script>
                  </head>
                </html>
                """;

        ImportedRecipe imported = recipeImportService.parseHtml("https://example.com/tacos", html);

        assertThat(imported.title()).isEqualTo("Weeknight Tacos");
        assertThat(imported.imageUrl()).isEqualTo("https://cdn.example.com/tacos.jpg");
        assertThat(imported.ingredients()).containsExactly("1 lb beef", "8 tortillas");
        assertThat(imported.instructions()).containsExactly("Brown beef", "Serve in tortillas");
        assertThat(imported.sourceUrl()).isEqualTo("https://example.com/tacos");
    }

    @Test
    void parseHtml_extractsHowToStepInstructionText() {
        String html = """
                <script type="application/ld+json">
                {
                  "@type":"Recipe",
                  "name":"Pancakes",
                  "recipeInstructions":[
                    {"@type":"HowToStep","text":"Mix batter"},
                    {"@type":"HowToStep","name":"Cook on griddle"}
                  ]
                }
                </script>
                """;

        ImportedRecipe imported = recipeImportService.parseHtml("https://example.com/pancakes", html);

        assertThat(imported.instructions()).containsExactly("Mix batter", "Cook on griddle");
    }

    @Test
    void importFromUrl_rejectsLoopbackTargetBeforeFetching() {
        assertThatThrownBy(() -> recipeImportService.importFromUrl("http://127.0.0.1:8080/private"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Could not import recipe");

        verifyNoInteractions(httpClient);
    }

    @Test
    void importFromUrl_rejectsPrivateNetworkTargetBeforeFetching() {
        for (String url : List.of(
                "http://10.0.0.1/recipe",
                "http://172.16.0.1/recipe",
                "http://192.168.1.10/recipe",
                "http://169.254.1.10/recipe"
        )) {
            assertThatThrownBy(() -> recipeImportService.importFromUrl(url))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Could not import recipe");
        }

        verifyNoInteractions(httpClient);
    }

    @Test
    void importFromUrl_rejectsUnsupportedSchemeBeforeFetching() {
        assertThatThrownBy(() -> recipeImportService.importFromUrl("file:///etc/passwd"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Could not import recipe");

        verifyNoInteractions(httpClient);
    }
}
