import java.util.LinkedHashMap;
import java.util.Map;

final class MemCache {

    private final static int MAX_SIZE = 100;

    private final Map<GeoPointPair, Directions> map = new LinkedHashMap<GeoPointPair, Directions>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GeoPointPair, Directions> eldest) {
            return size() > MAX_SIZE;
        }
    };

    void insertDirections(GeoPointPair geoPointsPair, Directions directions) {
        map.put(geoPointsPair, directions);
    }

    Directions getDirections(GeoPointPair geoPointsPair) {
        return map.get(geoPointsPair);
    }

}
