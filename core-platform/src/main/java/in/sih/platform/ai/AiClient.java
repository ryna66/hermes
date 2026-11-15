package in.sih.platform.ai;

import in.sih.platform.challenge.Category;
public interface AiClient {
    record Classification(Category category, double confidence) { }
    record Embedding(float[] embedding, String model, int dimensions, double confidence) { }
    record Draft(String proposal, double confidence) { }
    default Draft draftProposal(String title, String description, String category, String university, java.util.List<String> expertise) {
        throw new IllegalStateException("Proposal AI unavailable");
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record Transcription(String text, double confidence) { }
    default Transcription transcribe(String audioBase64, String mimeType, String language) { throw new IllegalStateException("Transcription unavailable"); }
    Classification classify(String title, String description);
    Embedding embed(String text);
}
