package in.sih.platform.matching;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.sih.platform.challenge.Challenge;
import org.springframework.stereotype.Component;
@Component
public class VectorSupport {
    private final ObjectMapper json;
    private final SimilarityService similarity;
    public VectorSupport(ObjectMapper json, SimilarityService similarity) { this.json=json; this.similarity=similarity; }
    public Double compare(Challenge a, Challenge b) { return compare(a, b.getEmbeddingText(), b.getEmbeddingModel()); }
    public Double compare(Challenge a, String vector, String model) {
        if (model == null || !model.equals(a.getEmbeddingModel()) || vector == null || a.getEmbeddingText() == null) return null;
        try { return similarity.cosine(json.readValue(a.getEmbeddingText(), float[].class), json.readValue(vector, float[].class)); }
        catch (Exception e) { return null; }
    }
}
