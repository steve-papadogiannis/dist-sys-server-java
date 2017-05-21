import java.io.Serializable;

final class GeoPointPair implements Serializable {

    private final GeoPoint startGeoPoint;
    private final GeoPoint endGeoPoint;

    GeoPointPair(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
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
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Double.valueOf(startGeoPoint.getLatitude()).hashCode();
        hash = 11 * hash + Double.valueOf(startGeoPoint.getLongitude()).hashCode();
        hash = 11 * hash + Double.valueOf(endGeoPoint.getLatitude()).hashCode();
        hash = 11 * hash + Double.valueOf(endGeoPoint.getLongitude()).hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof GeoPointPair)) {
            return false;
        }

        final GeoPointPair other = (GeoPointPair) obj;

        return Double.doubleToLongBits(startGeoPoint.getLatitude()) ==
                    Double.doubleToLongBits(other.startGeoPoint.getLatitude()) &&
               Double.doubleToLongBits(startGeoPoint.getLongitude()) ==
                    Double.doubleToLongBits(other.startGeoPoint.getLongitude()) &&
               Double.doubleToLongBits(endGeoPoint.getLatitude()) ==
                    Double.doubleToLongBits(other.endGeoPoint.getLatitude()) &&
               Double.doubleToLongBits(endGeoPoint.getLongitude()) ==
                    Double.doubleToLongBits(other.endGeoPoint.getLongitude());
    }

}
