package gr.papadogiannis.stefanos;

import com.google.maps.model.DirectionsResult;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
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
