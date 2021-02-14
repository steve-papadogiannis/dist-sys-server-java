package gr.papadogiannis.stefanos.mappers;

import gr.papadogiannis.stefanos.models.GeoPointPair;
import gr.papadogiannis.stefanos.models.GeoPoint;
import gr.papadogiannis.stefanos.workers.Worker;
import com.google.maps.model.DirectionsResult;

import java.util.List;
import java.util.Map;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public interface MapWorker extends Worker {

    List<Map<GeoPointPair, DirectionsResult>> map(GeoPoint startGeoPoint, GeoPoint endGeoPoint);

    void notifyMaster();

    long calculateHash(String string);

    void sendToReducers(List<Map<GeoPointPair, DirectionsResult>> map);

}
