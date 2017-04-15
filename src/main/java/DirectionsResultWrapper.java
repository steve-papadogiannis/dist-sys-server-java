import com.google.maps.model.DirectionsResult;

final class DirectionsResultWrapper {

    private final GeoPoint startPoint;
    private final GeoPoint endPoint;
    private final DirectionsResult directionsResult;

    DirectionsResultWrapper(GeoPoint startPoint, GeoPoint endPoint, DirectionsResult directionsResult) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.directionsResult = directionsResult;
    }

    GeoPoint getStartPoint() {
        return startPoint;
    }

    GeoPoint getEndPoint() {
        return endPoint;
    }

    public DirectionsResult getDirectionsResult() {
        return directionsResult;
    }

}
