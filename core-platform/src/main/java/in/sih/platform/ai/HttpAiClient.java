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
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
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
