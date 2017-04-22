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
    private Socket socket;
    private boolean isNotFinished = true;
    private ServerSocket serverSocket;

    ReduceWorkerImpl(String name, int port) {
        System.out.println("ReduceWorker " + name + " was created.");
        this.name = name;
        this.port = port;
    }

    @Override
    public void waitForMasterAck() {

    }

    @Override
    public void reduce(Map<GeoPointPair, List<DirectionsResult>> incoming) {
        incoming.forEach((x,y) -> {
            if (mapToReturn.containsKey(x)) {
                mapToReturn.get(x).addAll(y);
            } else {
                mapToReturn.put(x, y);
            }
        });
    }

    @Override
    public void sendResults(Map<GeoPointPair, List<DirectionsResult>> map) {
        System.out.println("Sending result " + mapToReturn + " to master from " + ApplicationConstants.MOSCOW + " ... ");
        try {
            objectOutputStream.writeObject(mapToReturn);
            objectOutputStream.flush();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize() {
        System.out.println("ReducerWorker " + name + " is waiting for tasks at port " + port + " ... ");
        try {
            serverSocket = new ServerSocket(port);
            while (isNotFinished) {
                socket = null;
                try {
                    socket = serverSocket.accept();
                    objectInputStream = new ObjectInputStream(socket.getInputStream());
                    objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                    waitForTasksThread();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            isNotFinished = false;
            try {
                if (objectOutputStream != null)
                    objectInputStream.close();
                if (objectOutputStream != null)
                    objectOutputStream.close();
                if (socket != null)
                    socket.close();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void waitForTasksThread() {
        Object incomingObject;
        try {
            while (isNotFinished) {
                incomingObject = objectInputStream.readObject();
                if (incomingObject instanceof String) {
                    final String inputLine = (String) incomingObject;
                    System.out.println(name + " received " + inputLine);
                    if (inputLine.equals("ack")) {
                        sendResults(mapToReturn);
                    } else if (inputLine.equals("exit")) {
                        isNotFinished = false;
                        objectInputStream.close();
                        objectOutputStream.close();
                        socket.close();
                        serverSocket.close();
                    }
                } else if (incomingObject instanceof Map) {
                    final Map<GeoPointPair, List<DirectionsResult>> incoming
                            = (Map<GeoPointPair, List<DirectionsResult>>) incomingObject;
                    reduce(incoming);
                    System.out.println(name + " received " + incoming);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            isNotFinished = false;
            try {
                if (objectOutputStream != null)
                    objectInputStream.close();
                if (objectOutputStream != null)
                    objectOutputStream.close();
                if (socket != null)
                    socket.close();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        initialize();
        System.out.println("ReduceWorker " + name + " is exiting...");
    }

}
