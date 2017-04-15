import com.google.maps.model.DirectionsResult;

public interface Master {

    void initialize();

    void waitForNewQueriesThread();

    Directions searchCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void distributeToMappers(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void waitForMappers();

    void ackToReducers();

    void collectDataFromReducer();

    Directions askGoogleDirectionsAPI(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    boolean updateCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint, Directions directions);

    boolean updateDatabase(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions);

}
