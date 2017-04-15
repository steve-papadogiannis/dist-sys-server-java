import javafx.util.Pair;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MemCache {

    private final static int MAX_SIZE = 100;

    private final Map<Pair<GeoPoint, GeoPoint>, Directions> map = new LinkedHashMap<Pair<GeoPoint, GeoPoint>, Directions>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Pair<GeoPoint, GeoPoint>, Directions> eldest) {
            return size() > MAX_SIZE;
        }
    };

    public void insertDirections(Pair<GeoPoint, GeoPoint> geoPointsPair, Directions directions) {
        map.put(geoPointsPair, directions);
    }

    public Directions getDirections(Pair<GeoPoint, GeoPoint> geoPointsPair) {
        return map.get(geoPointsPair);
    }

}
