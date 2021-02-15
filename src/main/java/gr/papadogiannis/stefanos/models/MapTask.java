package gr.papadogiannis.stefanos.models;

import java.io.Serializable;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public final class MapTask implements Serializable {

    private final GeoPoint startGeoPoint, endGeoPoint;

    public MapTask(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        this.startGeoPoint = startGeoPoint;
        this.endGeoPoint = endGeoPoint;
    }

    public GeoPoint getStartGeoPoint() {
        return startGeoPoint;
    }

    public GeoPoint getEndGeoPoint() {
        return endGeoPoint;
    }

    @Override
    public String toString() {
        return String.format("MapTask [ %s, %s ]", startGeoPoint, endGeoPoint);
    }

}
