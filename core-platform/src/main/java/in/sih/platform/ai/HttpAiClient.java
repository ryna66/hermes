package in.sih.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpAiClient implements AiClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final Duration timeout;
    public HttpAiClient(ObjectMapper json, @Value("${ai.base-url:http://localhost:8000}") String baseUrl,
                        @Value("${ai.timeout:2s}") Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("AI timeout must be positive");
        this.json = json; this.baseUrl = baseUrl; this.timeout = timeout;
        // Uvicorn serves HTTP/1.1; avoid an h2c upgrade losing the request body.
        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private <T> T post(String path, Object body, Class<T> type) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("AI returned HTTP " + response.statusCode());
            var document = json.readTree(response.body());
            if (document == null || !document.path("confidence").isNumber()) throw new IllegalStateException("Missing confidence");
            return json.treeToValue(document, type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("AI interrupted", e);
        } catch (Exception e) { throw new IllegalStateException("AI unavailable or invalid response", e); }
    }
    public Draft draftProposal(String title, String description, String category, String university, java.util.List<String> expertise) {
        Draft result = post("/draft-proposal", Map.of("challenge", Map.of("title", title, "description", description, "category", category),
                "university", Map.of("name", university, "expertise", expertise)), Draft.class);
        if (result == null || result.proposal() == null || result.proposal().isBlank() || !validConfidence(result.confidence()))
            throw new IllegalStateException("Invalid proposal");
        return result;
    }
    public Transcription transcribe(String audioBase64, String mimeType, String language) {
        return post("/transcribe", Map.of("audio_base64", audioBase64, "mime_type", mimeType, "language", language), Transcription.class);
    }
    public Classification classify(String title, String description) {
        Classification result = post("/classify", Map.of("title", title, "description", description), Classification.class);
        if (result == null || result.category() == null || !validConfidence(result.confidence())) throw new IllegalStateException("Invalid classification");
        return result;
    }
    public Embedding embed(String text) {
        Embedding result = post("/embed", Map.of("text", text), Embedding.class);
        validate(result); return result;
    }
    public static boolean validConfidence(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    public static void validate(Embedding e) {
        if (e == null || e.model() == null || e.model().isBlank() || e.embedding() == null || e.dimensions() < 1 ||
                e.dimensions() != e.embedding().length || !validConfidence(e.confidence())) throw new IllegalStateException("Invalid embedding");
        for (float value : e.embedding()) if (!Float.isFinite(value)) throw new IllegalStateException("Invalid vector component");
    }
}
