package gr.papadogiannis.stefanos.caches;

import gr.papadogiannis.stefanos.models.GeoPointPair;
import com.google.maps.model.DirectionsResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public final class MemCache {

    private final static int MAX_SIZE = 100;

    private final Map<GeoPointPair, DirectionsResult> map = new LinkedHashMap<GeoPointPair, DirectionsResult>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GeoPointPair, DirectionsResult> eldest) {
            return size() > MAX_SIZE;
        }
    };

    public void insertDirections(GeoPointPair geoPointsPair, DirectionsResult directions) {
        map.put(geoPointsPair, directions);
    }

    public DirectionsResult getDirections(GeoPointPair geoPointsPair) {
        return map.get(geoPointsPair);
    }

}
