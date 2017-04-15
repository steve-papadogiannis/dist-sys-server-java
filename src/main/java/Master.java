import com.google.maps.model.DirectionsResult;

public interface Master {

    void initialize();

    void waitForNewQueriesThread();

    DirectionsResult searchCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void distributeToMappers(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void waitForMappers();

    void ackToReducers();

    void collectDataFromReducer();

    DirectionsResult askGoogleDirectionsAPI(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    boolean updateCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions);

    boolean updateDatabase(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions);

}
