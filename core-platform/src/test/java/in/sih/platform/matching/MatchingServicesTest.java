package in.sih.platform.matching;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MatchingServicesTest {
    private final GeoService geo = new HaversineGeoService();
    private final SimilarityService similarity = new CosineSimilarityService();
    @Test void knownDistanceAndSymmetry() {
        double distance = geo.distanceKm(51.5074, -0.1278, 48.8566, 2.3522);
        assertEquals(343.56, distance, 0.1);
        assertEquals(distance, geo.distanceKm(48.8566, 2.3522, 51.5074, -0.1278), 1e-9);
        assertEquals(0, geo.distanceKm(20, 77, 20, 77));
    }
    @Test void dateLineAndAntipodes() {
        assertEquals(222.39, geo.distanceKm(0, 179, 0, -179), 0.1);
        assertEquals(20015.11, geo.distanceKm(0, 0, 0, 180), 0.1);
    }
    @Test void invalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> geo.distanceKm(91, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> geo.distanceKm(0, -181, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> geo.distanceKm(Double.NaN, 0, 0, 0));
    }
    @Test void cosineGeometry() {
        assertEquals(1, similarity.cosine(new float[]{1,2}, new float[]{2,4}), 1e-12);
        assertEquals(0, similarity.cosine(new float[]{1,0}, new float[]{0,1}));
        assertEquals(-1, similarity.cosine(new float[]{1,0}, new float[]{-1,0}));
        assertEquals(0, similarity.cosine(new float[]{0,0}, new float[]{1,2}));
        assertEquals(1, similarity.cosine(new float[]{Float.MAX_VALUE}, new float[]{Float.MAX_VALUE}), 1e-12);
    }
    @Test void invalidVectors() {
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(null, new float[]{1}));
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(new float[]{}, new float[]{}));
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(new float[]{1}, new float[]{1,2}));
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(new float[]{Float.NaN}, new float[]{1}));
    }
}
