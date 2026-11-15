package in.sih.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.sih.platform.challenge.Challenge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChallengeEnrichment {
    private final AiClient ai;
    private final ObjectMapper json;
    private final double threshold;
    public ChallengeEnrichment(AiClient ai, ObjectMapper json, @Value("${ai.confidence-threshold:0.70}") double threshold) {
        this.ai = ai; this.json = json; this.threshold = threshold;
    }
    public void enrich(Challenge c) {
        boolean review = false;
        try {
            var result = ai.classify(c.getTitle(), c.getDescription());
            if (result == null || result.category() == null || !HttpAiClient.validConfidence(result.confidence())) throw new IllegalStateException();
            c.setSuggestedCategory(result.category()); c.setClassificationConfidence(result.confidence());
            review = result.confidence() < threshold;
        } catch (RuntimeException e) { review = true; }
        try {
            var result = ai.embed(c.getTitle() + " " + c.getDescription()); HttpAiClient.validate(result);
            if (result.confidence() < threshold) review = true;
            else { c.setEmbeddingText(json.writeValueAsString(result.embedding())); c.setEmbeddingModel(result.model()); }
        } catch (Exception e) { review = true; }
        c.setModerationRequired(review);
        c.setModerationReason(review ? "AI unavailable, invalid or below confidence threshold; manual review required" : null);
    }
}
