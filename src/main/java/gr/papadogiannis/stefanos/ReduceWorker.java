package gr.papadogiannis.stefanos;

import com.google.maps.model.DirectionsResult;

import java.util.List;
import java.util.Map;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public interface ReduceWorker extends Worker {

    void waitForMasterAck();

    void reduce(List<Map<GeoPointPair, DirectionsResult>> incoming);

    void sendResults(Map<GeoPointPair, List<DirectionsResult>> map);

}
