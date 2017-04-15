import com.google.maps.model.DirectionsResult;

import java.util.LinkedHashMap;
import java.util.Map;

final class MemCache {

    private final static int MAX_SIZE = 100;

    private final Map<GeoPointPair, DirectionsResult> map = new LinkedHashMap<GeoPointPair, DirectionsResult>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GeoPointPair, DirectionsResult> eldest) {
            return size() > MAX_SIZE;
        }
    };

    void insertDirections(GeoPointPair geoPointsPair, DirectionsResult directions) {
        map.put(geoPointsPair, directions);
    }

    DirectionsResult getDirections(GeoPointPair geoPointsPair) {
        return map.get(geoPointsPair);
    }

}
