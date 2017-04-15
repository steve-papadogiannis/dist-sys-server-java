final class GeoPointPair {

    private final GeoPoint startGeoPoint;
    private final GeoPoint endGeoPoint;

    GeoPointPair(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        this.startGeoPoint = startGeoPoint;
        this.endGeoPoint = endGeoPoint;
    }

    public GeoPoint getStartGeoPoint() {
        return startGeoPoint;
    }

    public GeoPoint getEndGeoPoint() {
        return endGeoPoint;
    }

}
