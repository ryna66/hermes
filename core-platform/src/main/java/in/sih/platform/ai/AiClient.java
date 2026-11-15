package in.sih.platform.ai;

import in.sih.platform.challenge.Category;
public interface AiClient {
    record Classification(Category category, double confidence) { }
    record Embedding(float[] embedding, String model, int dimensions, double confidence) { }
    record Draft(String proposal, double confidence) { }
    default Draft draftProposal(String title, String description, String category, String university, java.util.List<String> expertise) {
        throw new IllegalStateException("Proposal AI unavailable");
    }
    Classification classify(String title, String description);
    Embedding embed(String text);
}
