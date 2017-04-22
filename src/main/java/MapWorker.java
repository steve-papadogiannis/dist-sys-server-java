import com.google.maps.model.DirectionsResult;

import java.util.List;
import java.util.Map;

public interface MapWorker extends Worker {

    List<Map<GeoPointPair, DirectionsResult>> map(GeoPoint obj1, GeoPoint obj2);

    void notifyMaster();

    long calculateHash(String string);

    void sendToReducers(List<Map<GeoPointPair, DirectionsResult>> map);

}
