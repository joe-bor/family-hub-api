package com.familyhub.demo.service;

import com.familyhub.demo.exception.BadRequestException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
public class RecipeImportService {
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_RESPONSE_BYTES = 1_000_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String IMPORT_FAILURE_MESSAGE = "Could not import recipe.";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public RecipeImportService(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public RecipeImportService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public ImportedRecipe importFromUrl(String url) {
        try {
            URI uri = validatePublicHttpUri(url);
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                HttpResponse<InputStream> response = httpClient.send(buildRequest(uri), HttpResponse.BodyHandlers.ofInputStream());
                int statusCode = response.statusCode();
                if (isRedirect(statusCode)) {
                    if (redirects == MAX_REDIRECTS) {
                        throw importFailure();
                    }
                    uri = validatePublicHttpUri(resolveRedirect(uri, response));
                    continue;
                }
                if (statusCode < 200 || statusCode >= 300) {
                    throw importFailure();
                }

                String html = readCappedBody(response);
                return parseHtml(uri.toString(), html);
            }
            throw importFailure();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw importFailure();
        }
    }

    public ImportedRecipe parseHtml(String sourceUrl, String html) {
        try {
            Document document = Jsoup.parse(html, sourceUrl);
            for (Element script : document.select("script[type=application/ld+json]")) {
                Optional<JsonNode> recipeNode = findRecipeNode(objectMapper.readTree(script.data()));
                if (recipeNode.isPresent()) {
                    return toImportedRecipe(sourceUrl, document, recipeNode.get());
                }
            }
            throw importFailure();
        } catch (JacksonException ex) {
            throw importFailure();
        }
    }

    private HttpRequest buildRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .header("User-Agent", "FamilyHubRecipeImporter/1.0")
                .build();
    }

    private String readCappedBody(HttpResponse<InputStream> response) throws IOException {
        Optional<Long> contentLength = response.headers().firstValueAsLong("content-length").stream().boxed().findFirst();
        if (contentLength.isPresent() && contentLength.get() > MAX_RESPONSE_BYTES) {
            throw importFailure();
        }

        try (InputStream body = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw importFailure();
                }
                output.write(buffer, 0, read);
            }
            return output.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private URI resolveRedirect(URI currentUri, HttpResponse<InputStream> response) {
        String location = response.headers().firstValue("location")
                .orElseThrow(RecipeImportService::importFailure);
        return currentUri.resolve(location);
    }

    private URI validatePublicHttpUri(String url) {
        try {
            return validatePublicHttpUri(new URI(url.trim()));
        } catch (URISyntaxException | RuntimeException ex) {
            throw importFailure();
        }
    }

    private URI validatePublicHttpUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw importFailure();
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw importFailure();
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isPublicAddress(address)) {
                    throw importFailure();
                }
            }
        } catch (IOException ex) {
            throw importFailure();
        }
        return uri;
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0 || first == 10 || first == 127 || first >= 224) {
                return false;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 169 && second == 254) {
                return false;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return false;
            }
            if (first == 192 && (second == 0 || second == 168)) {
                return false;
            }
            if (first == 198 && (second == 18 || second == 19 || second == 51)) {
                return false;
            }
            return !(first == 203 && second == 0);
        }

        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) != 0xfc;
        }

        return false;
    }

    private Optional<JsonNode> findRecipeNode(JsonNode root) {
        if (isRecipe(root)) {
            return Optional.of(root);
        }
        if (root.isArray()) {
            return StreamSupport.stream(root.spliterator(), false)
                    .map(this::findRecipeNode)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }
        JsonNode graph = root.path("@graph");
        if (graph.isArray()) {
            return StreamSupport.stream(graph.spliterator(), false)
                    .filter(this::isRecipe)
                    .findFirst();
        }
        return Optional.empty();
    }

    private boolean isRecipe(JsonNode node) {
        JsonNode type = node.path("@type");
        if (type.isTextual()) {
            return "Recipe".equalsIgnoreCase(type.asText());
        }
        if (type.isArray()) {
            return StreamSupport.stream(type.spliterator(), false)
                    .anyMatch(candidate -> "Recipe".equalsIgnoreCase(candidate.asText()));
        }
        return false;
    }

    private ImportedRecipe toImportedRecipe(String sourceUrl, Document document, JsonNode recipeNode) {
        String title = textValue(recipeNode.path("name"));
        if (title == null || title.isBlank()) {
            title = document.title();
        }
        if (title == null || title.isBlank()) {
            throw importFailure();
        }

        return new ImportedRecipe(
                title.trim(),
                firstImage(recipeNode.path("image")),
                stringList(recipeNode.path("recipeIngredient")),
                instructionList(recipeNode.path("recipeInstructions")),
                null,
                sourceUrl,
                List.of(),
                false
        );
    }

    private String firstImage(JsonNode imageNode) {
        if (imageNode.isMissingNode() || imageNode.isNull()) {
            return null;
        }
        if (imageNode.isTextual()) {
            return blankToNull(imageNode.asText());
        }
        if (imageNode.isArray() && !imageNode.isEmpty()) {
            return firstImage(imageNode.get(0));
        }
        if (imageNode.isObject()) {
            String url = textValue(imageNode.path("url"));
            if (url == null) {
                url = textValue(imageNode.path("contentUrl"));
            }
            return blankToNull(url);
        }
        return null;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            String value = textValue(node);
            return value == null ? List.of() : List.of(value);
        }

        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            String value = textValue(element);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> instructionList(JsonNode node) {
        if (!node.isArray()) {
            String value = instructionText(node);
            return value == null ? List.of() : List.of(value);
        }

        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            if (element.path("itemListElement").isArray()) {
                values.addAll(instructionList(element.path("itemListElement")));
            } else {
                String value = instructionText(element);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private String instructionText(JsonNode node) {
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (node.isObject()) {
            String text = textValue(node.path("text"));
            if (text == null) {
                text = textValue(node.path("name"));
            }
            return blankToNull(text);
        }
        return null;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static BadRequestException importFailure() {
        return new BadRequestException(IMPORT_FAILURE_MESSAGE);
    }
}
