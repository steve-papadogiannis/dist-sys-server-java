package gr.papadogiannis.stefanos.reducers.impl;

import com.google.maps.model.DirectionsResult;
import gr.papadogiannis.stefanos.models.GeoPointPair;
import gr.papadogiannis.stefanos.reducers.ReduceWorker;

import java.util.concurrent.CountDownLatch;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public final class ReduceWorkerImpl implements ReduceWorker {

    private static final Logger LOGGER = Logger.getLogger(ReduceWorkerImpl.class.getName());

    private static final Map<GeoPointPair, List<DirectionsResult>> mapToReturn = new HashMap<>();
    private CountDownLatch countDownLatch = new CountDownLatch(4);
    private boolean isNotFinished = true;
    private ServerSocket serverSocket;
    private final int port;

    private ReduceWorkerImpl(int port) {
        LOGGER.info("ReduceWorker was created.");
        this.port = port;
    }

    public static void main(String[] args) {
        ReduceWorkerImpl reduceWorker = new ReduceWorkerImpl(Integer.parseInt(args[0]));
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
        this.isNotFinished = false;
    }

    @Override
    public void initialize() {
        System.out.println("ReducerWorker is waiting for tasks at port " + port + " ... ");
        try {
            serverSocket = new ServerSocket(port);
            ReduceWorkerImpl reduceWorker = this;
            while (isNotFinished) {
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

    public static void clearMapToReturn() {
        mapToReturn.clear();
    }

    private void run() {
        initialize();
        LOGGER.info(String.format("ReduceWorker %d is exiting...%n", port));
    }

    private class A implements Runnable {

        private ObjectOutputStream objectOutputStream;
        private ObjectInputStream objectInputStream;
        private final ReduceWorkerImpl reduceWorker;
        private final ServerSocket serverSocket;
        private boolean isNotFinished = true;
        private final Socket socket;

        A(Socket socket, ServerSocket serverSocket, ReduceWorkerImpl reduceWorker) {
            this.serverSocket = serverSocket;
            this.socket = socket;
            this.reduceWorker = reduceWorker;
        }

        @Override
        public void run() {
            try {
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectInputStream = new ObjectInputStream(socket.getInputStream());
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
                        LOGGER.info(String.format("%d received %s%n", port, inputLine));
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
//                                objectOutputStream.close();
                                socket.close();
                                break;
                            case "terminate":
                                reduceWorker.falsifyIsNotFinishedFlag();
                                isNotFinished = false;
                                objectInputStream.close();
//                                objectOutputStream.close();
                                socket.close();
                                if (serverSocket != null)
                                    serverSocket.close();
                                break;
                        }
                    } else if (incomingObject instanceof List) {
                        final List<Map<GeoPointPair, DirectionsResult>> incoming
                                = (List<Map<GeoPointPair, DirectionsResult>>) incomingObject;
                        reduce(incoming);
                        LOGGER.info(String.format("%d received %s%n", port, incoming));
                    }
                }
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                isNotFinished = false;
                try {
                    if (objectInputStream != null)
                        objectInputStream.close();
//                    if (objectOutputStream != null)
//                        objectOutputStream.close();
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        void sendResults() {
            LOGGER.info(String.format("Sending result %s to master from %d... %n", mapToReturn, port));
            try {
                objectOutputStream.writeObject(mapToReturn);
                objectOutputStream.flush();
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
