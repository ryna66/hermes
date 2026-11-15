package in.sih.platform.matching;
public interface SimilarityService {
    /** Returns zero when either vector has zero magnitude. */
    double cosine(float[] first, float[] second);
}
