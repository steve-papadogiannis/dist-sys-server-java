package gr.papadogiannis.stefanos.reducers.impl;

import com.google.maps.model.DirectionsResult;
import gr.papadogiannis.stefanos.constants.ApplicationConstants;
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
    public static final String REDUCE_WORKER_IS_EXITING_MESSAGE = "ReduceWorker %d is exiting...%n";
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
        LOGGER.info(String.format("ReducerWorker is waiting for tasks at port %d... %n", port));
        try {
            serverSocket = new ServerSocket(port);
            ReduceWorkerImpl reduceWorker = this;
            while (isNotFinished) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    new Thread(new ReduceWorkerRunnable(socket, serverSocket, reduceWorker)).start();
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
        LOGGER.info(String.format(REDUCE_WORKER_IS_EXITING_MESSAGE, port));
    }

    private static class ReduceWorkerRunnable implements Runnable {

        private static final String SENDING_RESULT_TO_MASTER_MESSAGE = "Sending result %s to master from %d...";
        private static final String RECEIVED_MESSAGE = "%d received %s";

        private CountDownLatch countDownLatch = new CountDownLatch(4);
        private ObjectOutputStream objectOutputStream;
        private ObjectInputStream objectInputStream;
        private final ReduceWorkerImpl reduceWorker;
        private final ServerSocket serverSocket;
        private boolean isNotFinished = true;
        private final Socket socket;

        ReduceWorkerRunnable(Socket socket, ServerSocket serverSocket, ReduceWorkerImpl reduceWorker) {
            this.serverSocket = serverSocket;
            this.reduceWorker = reduceWorker;
            this.socket = socket;
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
                        LOGGER.info(String.format(RECEIVED_MESSAGE, serverSocket.getLocalPort(), inputLine));
                        switch (inputLine) {
                            case ApplicationConstants.ACK_SIGNAL:
                                countDownLatch.await();
                                sendResults();
                                countDownLatch = new CountDownLatch(4);
                                break;
                            case ApplicationConstants.EXIT_SIGNAL:
                                countDownLatch.countDown();
                                isNotFinished = false;
                                objectInputStream.close();
//                                objectOutputStream.close();
                                socket.close();
                                break;
                            case ApplicationConstants.TERMINATE_SIGNAL:
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
                        LOGGER.info(String.format(RECEIVED_MESSAGE, serverSocket.getLocalPort(), incoming));
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
            LOGGER.info(String.format(SENDING_RESULT_TO_MASTER_MESSAGE, mapToReturn, serverSocket.getLocalPort()));
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
