import com.google.maps.model.DirectionsResult;
import java.util.Map;

public interface MapWorker extends Worker {

    Map<GeoPointPair, DirectionsResult> map(Object obj1, Object obj2);

    void notifyMaster();

    long calculateHash(String string);

    void sendToReducers(Map<GeoPointPair, DirectionsResult> map);

}
