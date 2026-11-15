package in.sih.platform.matching;
import org.springframework.stereotype.Service;
@Service
public class CosineSimilarityService implements SimilarityService {
    @Override
    public double cosine(float[] first, float[] second) {
        if (first == null || second == null || first.length == 0 || first.length != second.length)
            throw new IllegalArgumentException("Vectors must be nonempty and have equal dimensions");
        double dot = 0, norm1 = 0, norm2 = 0;
        for (int i = 0; i < first.length; i++) {
            if (!Float.isFinite(first[i]) || !Float.isFinite(second[i]))
                throw new IllegalArgumentException("Vector components must be finite");
            double x = first[i], y = second[i];
            dot += x * y; norm1 += x * x; norm2 += y * y;
        }
        if (norm1 == 0 || norm2 == 0) return 0;
        return Math.clamp(dot / (Math.sqrt(norm1) * Math.sqrt(norm2)), -1, 1);
    }
}
