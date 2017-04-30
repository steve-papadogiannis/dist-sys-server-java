import com.google.maps.model.DirectionsResult;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

public final class ReduceWorkerImpl implements ReduceWorker {

    private final String name;
    private final int port;
    private Map<GeoPointPair, List<DirectionsResult>> mapToReturn = new HashMap<>();
    private boolean isNotFinished2 = true;
    private ServerSocket serverSocket;
    private CountDownLatch countDownLatch = new CountDownLatch(4);

    private ReduceWorkerImpl(String name, int port) {
        System.out.println("ReduceWorker " + name + " was created.");
        this.name = name;
        this.port = port;
    }

    public static void main(String[] args) {
        ReduceWorkerImpl reduceWorker = new ReduceWorkerImpl(args[0], Integer.parseInt(args[1]));
        reduceWorker.run();
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

    private void falsifyIsNotFinishedFlag() {
        this.isNotFinished2 = false;
    }

    @Override
    public void initialize() {
        System.out.println("ReducerWorker " + name + " is waiting for tasks at port " + port + " ... ");
        try {
            serverSocket = new ServerSocket(port);
            ReduceWorkerImpl reduceWorker = this;
            while (isNotFinished2) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    new Thread(new A(socket, serverSocket, reduceWorker)).start();
                } catch (SocketException ex) {
                    System.out.println("Server socket on reduce worker was closed.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
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

    private void run() {
        initialize();
        System.out.println("ReduceWorker " + name + " is exiting...");
    }

    private class A implements Runnable {

        private Socket socket;
        private ServerSocket serverSocket;
        private ObjectInputStream objectInputStream;
        private ObjectOutputStream objectOutputStream;
        private boolean isNotFinished = true;
        private ReduceWorkerImpl reduceWorker;

        A(Socket socket, ServerSocket serverSocket, ReduceWorkerImpl reduceWorker) {
            this.serverSocket = serverSocket;
            this.socket = socket;
            this.reduceWorker = reduceWorker;
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

        void waitForTasksThread() {
            Object incomingObject;
            try {
                while (isNotFinished) {
                    incomingObject = objectInputStream.readObject();
                    if (incomingObject instanceof String) {
                        final String inputLine = (String) incomingObject;
                        System.out.println(name + " received " + inputLine);
                        switch (inputLine) {
                            case "ack":
                                countDownLatch.await();
                                sendResults();
                                countDownLatch = new CountDownLatch(4);
                                break;
                            case "exit":
                                countDownLatch.countDown();
                                isNotFinished = false;
                                objectInputStream.close();
                                objectOutputStream.close();
                                socket.close();
                                break;
                            case "terminate":
                                reduceWorker.falsifyIsNotFinishedFlag();
                                isNotFinished = false;
                                objectInputStream.close();
                                objectOutputStream.close();
                                socket.close();
                                if (serverSocket != null)
                                    serverSocket.close();
                                break;
                        }
                    } else if (incomingObject instanceof List) {
                        final List<Map<GeoPointPair, DirectionsResult>> incoming
                                = (List<Map<GeoPointPair, DirectionsResult>>) incomingObject;
                        reduce(incoming);
                        System.out.println(name + " received " + incoming);
                    }
                }
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
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

        void sendResults() {
            System.out.println("Sending result " + mapToReturn + " to master from " + ApplicationConstants.MOSCOW + " ... ");
            try {
                objectOutputStream.writeObject(mapToReturn);
                objectOutputStream.flush();
                mapToReturn.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void reduce(List<Map<GeoPointPair, DirectionsResult>> incoming) {
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
