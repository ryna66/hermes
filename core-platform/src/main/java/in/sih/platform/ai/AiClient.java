package in.sih.platform.ai;

import in.sih.platform.challenge.Category;
public interface AiClient {
    record Classification(Category category, double confidence) { }
    record Embedding(float[] embedding, String model, int dimensions, double confidence) { }
    Classification classify(String title, String description);
    Embedding embed(String text);
}
