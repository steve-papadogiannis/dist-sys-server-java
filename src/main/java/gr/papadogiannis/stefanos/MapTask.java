package gr.papadogiannis.stefanos;

import java.io.Serializable;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public final class MapTask implements Serializable {

    private final GeoPoint startGeoPoint, endGeoPoint;

    MapTask(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        this.startGeoPoint = startGeoPoint;
        this.endGeoPoint = endGeoPoint;
    }

    GeoPoint getStartGeoPoint() {
        return startGeoPoint;
    }

    GeoPoint getEndGeoPoint() {
        return endGeoPoint;
    }

    @Override
    public String toString() {
        return String.format("MapTask [ %s, %s ]", startGeoPoint, endGeoPoint);
    }

}
