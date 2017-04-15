import com.google.maps.model.DirectionsResult;
import javafx.util.Pair;

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
    private Map<Pair<GeoPoint, GeoPoint>, List<DirectionsResult>> forReduceMap = new HashMap<>();

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
    public void sendResults(Map<Integer, Object> map) {

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
                } else if (incomingObject instanceof Map) {
                    final Map<Pair<GeoPoint, GeoPoint>, DirectionsResult> casObject
                            = (Map<Pair<GeoPoint, GeoPoint>, DirectionsResult>) incomingObject;
                    System.out.println(name + " received " + casObject);
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
