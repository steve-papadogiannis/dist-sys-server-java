import com.google.maps.model.DirectionsResult;

public interface Master {

    void initialize();

    DirectionsResult searchCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void distributeToMappers(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void waitForMappers();

    void ackToReducers();

    void collectDataFromReducer();

    DirectionsResult askGoogleDirectionsAPI(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void updateCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions);

    void updateDatabase(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions);

}
