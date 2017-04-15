import com.google.maps.model.DirectionsResult;

import java.util.List;
import java.util.Map;

public interface ReduceWorker extends Worker{

    void waitForMasterAck();

    Map<String, Object> reduce(String string, Object obj1);

    void sendResults(Map<GeoPointPair, List<DirectionsResult>> map);

}
