import com.google.maps.model.DirectionsResult;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReduceWorkerImpl implements ReduceWorker {

    private final String name;
    private final int port;
    private Map<GeoPointPair, List<DirectionsResult>> mapToReturn = new HashMap<>();
    private Socket socket;
    private boolean isNotFinished2 = true;
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
    public void reduce(List<Map<GeoPointPair, DirectionsResult>> incoming) {

    }

    @Override
    public void sendResults(Map<GeoPointPair, List<DirectionsResult>> map) {

    }

    @Override
    public void initialize() {
        System.out.println("ReducerWorker " + name + " is waiting for tasks at port " + port + " ... ");
        try {
            serverSocket = new ServerSocket(port);
            while (isNotFinished2) {
                socket = null;
                try {
                    socket = serverSocket.accept();
                    new Thread(new A(socket, serverSocket)).start();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            isNotFinished2 = false;
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void waitForTasksThread() {

    }

    @Override
    public void run() {
        initialize();
        System.out.println("ReduceWorker " + name + " is exiting...");
    }

    private class A implements Runnable {

        private Socket socket;
        private ServerSocket serverSocket;
        private ObjectInputStream objectInputStream;
        private ObjectOutputStream objectOutputStream;
        private boolean isNotFinished = true;

        public A(Socket socket, ServerSocket serverSocket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                objectInputStream = new ObjectInputStream(socket.getInputStream());
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                waitForTasksThread();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
                        } else if (inputLine.equals("terminate")) {
                            isNotFinished2 = false;
                            objectInputStream.close();
                            objectOutputStream.close();
                            socket.close();
                            if (serverSocket != null)
                                serverSocket.close();
                        }
                    } else if (incomingObject instanceof List) {
                        final List<Map<GeoPointPair, DirectionsResult>> incoming
                                = (List<Map<GeoPointPair, DirectionsResult>>) incomingObject;
                        reduce(incoming);
                        System.out.println(name + " received " + incoming);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                isNotFinished = false;
                try {
                    if (objectInputStream != null)
                        objectInputStream.close();
                    if (objectOutputStream != null)
                        objectOutputStream.close();
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void sendResults(Map<GeoPointPair, List<DirectionsResult>> map) {
            System.out.println("Sending result " + mapToReturn + " to master from " + ApplicationConstants.MOSCOW + " ... ");
            try {
                objectOutputStream.writeObject(mapToReturn);
                objectOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void reduce(List<Map<GeoPointPair, DirectionsResult>> incoming) {
            incoming.forEach(x -> {
                final GeoPointPair geoPointPair = x.keySet().stream().findFirst().orElse(null);
                if (geoPointPair != null) {
                    final List<DirectionsResult> list = new ArrayList<>();
                    list.add(x.get(geoPointPair));
                    if (mapToReturn.containsKey(geoPointPair)) {
                        mapToReturn.get(geoPointPair).add(x.get(geoPointPair));
                    } else {
                        mapToReturn.put(geoPointPair, list);
                    }
                }
            });
        }

    }
}
