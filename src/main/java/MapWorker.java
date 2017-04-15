import com.google.maps.model.DirectionsResult;
import javafx.util.Pair;

import java.util.Map;

public interface MapWorker extends Worker {

    Map<Pair<GeoPoint, GeoPoint>, DirectionsResult> map(Object obj1, Object obj2);

    void notifyMaster();

    long calculateHash(String string);

    void sendToReducers(Map<Pair<GeoPoint, GeoPoint>, DirectionsResult> map);

}
