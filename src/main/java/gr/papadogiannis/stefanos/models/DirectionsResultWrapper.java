package gr.papadogiannis.stefanos.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.maps.model.DirectionsResult;
import org.mongojack.ObjectId;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public final class DirectionsResultWrapper {

    private String id;

    private GeoPoint startPoint;

    private GeoPoint endPoint;

    private DirectionsResult directionsResult;

    public DirectionsResultWrapper() {
    }

    public DirectionsResultWrapper(GeoPoint startPoint, GeoPoint endPoint, DirectionsResult directionsResult) {
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
    public GeoPoint getStartPoint() {
        return startPoint;
    }

    @JsonProperty("endPoint")
    public GeoPoint getEndPoint() {
        return endPoint;
    }

    @JsonProperty("directionsResult")
    public DirectionsResult getDirectionsResult() {
        return directionsResult;
    }

}
