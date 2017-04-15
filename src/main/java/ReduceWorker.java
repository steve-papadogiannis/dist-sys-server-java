import java.util.Map;

public interface ReduceWorker extends Worker{

    void waitForMasterAck();

    Map<String, Object> reduce(String string, Object obj1);

    void sendResults(Map<Integer, Object> map);

}
