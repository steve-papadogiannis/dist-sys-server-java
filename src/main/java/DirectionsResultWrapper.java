import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.maps.model.DirectionsResult;
import org.mongojack.ObjectId;

final class DirectionsResultWrapper {

    private String id;

    private GeoPoint startPoint;

    private GeoPoint endPoint;

    private DirectionsResult directionsResult;

    DirectionsResultWrapper() { }

    DirectionsResultWrapper(GeoPoint startPoint, GeoPoint endPoint, DirectionsResult directionsResult) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.directionsResult = directionsResult;
    }

    @JsonProperty("startPoint")
    public void setStartPoint(GeoPoint startPoint) {
        this.startPoint = startPoint;
    }

    @JsonProperty("endPoint")
    public void setEndPoint(GeoPoint endPoint) {
        this.endPoint = endPoint;
    }

    @ObjectId
    @JsonProperty("_id")
    public String getId() {
        return id;
    }
    @ObjectId
    @JsonProperty("_id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("directionsResult")
    public void setDirectionsResult(DirectionsResult directionsResult) {
        this.directionsResult = directionsResult;
    }

    @JsonProperty("startPoint")
    GeoPoint getStartPoint() {
        return startPoint;
    }

    @JsonProperty("endPoint")
    GeoPoint getEndPoint() {
        return endPoint;
    }

    @JsonProperty("directionsResult")
    DirectionsResult getDirectionsResult() {
        return directionsResult;
    }

}
