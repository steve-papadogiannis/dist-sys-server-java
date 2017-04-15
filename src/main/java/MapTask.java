import java.io.Serializable;

public final class MapTask implements Serializable{

    private final GeoPoint startGeopoint, endGeoPoint;

    MapTask(GeoPoint startGeopoint, GeoPoint endGeoPoint) {
        this.startGeopoint = startGeopoint;
        this.endGeoPoint = endGeoPoint;
    }

    GeoPoint getStartGeopoint() {
        return startGeopoint;
    }

    GeoPoint getEndGeoPoint() {
        return endGeoPoint;
    }

    @Override
    public String toString() {
        return "MapTask [ " + startGeopoint + ", " + endGeoPoint + " ]";
    }

}
