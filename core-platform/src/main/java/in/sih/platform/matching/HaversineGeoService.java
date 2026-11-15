package in.sih.platform.matching;
import org.springframework.stereotype.Service;
@Service
public class HaversineGeoService implements GeoService {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    @Override
    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        validate(lat1, lon1); validate(lat2, lon2);
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1))
            * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.clamp(a, 0, 1)));
    }
    private void validate(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
            throw new IllegalArgumentException("Coordinates must be finite; latitude within [-90,90], longitude within [-180,180]");
    }
}
