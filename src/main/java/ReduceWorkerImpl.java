import com.google.maps.model.DirectionsResult;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReduceWorkerImpl implements ReduceWorker {

    private final String name;
    private final int port;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;
    private Map<GeoPointPair, List<DirectionsResult>> mapToReturn = new HashMap<>();

    ReduceWorkerImpl(String name, int port) {
        System.out.println("ReduceWorker " + name + " was created.");
        this.name = name;
        this.port = port;
    }

    @Override
    public void waitForMasterAck() {

    }

    @Override
    public Map<String, Object> reduce(String string, Object obj1) {
        return null;
    }

    @Override
    public void sendResults(Map<GeoPointPair, List<DirectionsResult>> map) {
        System.out.println("Sending ack to reduce worker " + ApplicationConstants.MOSCOW + " ... ");
        try {
            objectOutputStream.writeObject(mapToReturn);
            objectOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize() {
        System.out.println("ReducerWorker " + name + " is waiting for tasks at port " + port + " ... ");
        try {
            final ServerSocket serverSocket = new ServerSocket(port);
            while(true) {
                final Socket socket = serverSocket.accept();
                objectInputStream = new ObjectInputStream(socket.getInputStream());
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                waitForTasksThread();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void waitForTasksThread() {
        Object incomingObject;
        try {
            while ((incomingObject = objectInputStream.readObject()) != null) {
                if (incomingObject instanceof String) {
                    final String inputLine = (String) incomingObject;
                    System.out.println(name + " received " + inputLine);
                    if (inputLine.equals("ack")) {
                        sendResults(mapToReturn);
                    }
                    break;
                } else if (incomingObject instanceof Map) {
                    final Map<GeoPointPair, List<DirectionsResult>> incoming
                            = (Map<GeoPointPair, List<DirectionsResult>>) incomingObject;
                    mapToReturn.putAll(incoming);
                    System.out.println(name + " received " + incoming);
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        initialize();
    }

}
