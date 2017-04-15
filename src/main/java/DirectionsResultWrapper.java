import com.google.maps.model.DirectionsResult;
import org.mongojack.ObjectId;

import java.io.Serializable;

final class DirectionsResultWrapper implements Serializable {

    @ObjectId
    private String _id;

    private GeoPoint startPoint;

    private GeoPoint endPoint;

    private DirectionsResult directionsResult;

    DirectionsResultWrapper() { }

    DirectionsResultWrapper(GeoPoint startPoint, GeoPoint endPoint, DirectionsResult directionsResult) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.directionsResult = directionsResult;
    }

    public void setStartPoint(GeoPoint startPoint) {
        this.startPoint = startPoint;
    }

    public void setEndPoint(GeoPoint endPoint) {
        this.endPoint = endPoint;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public void setDirectionsResult(DirectionsResult directionsResult) {
        this.directionsResult = directionsResult;
    }

    GeoPoint getStartPoint() {
        return startPoint;
    }

    GeoPoint getEndPoint() {
        return endPoint;
    }

    DirectionsResult getDirectionsResult() {
        return directionsResult;
    }

}
