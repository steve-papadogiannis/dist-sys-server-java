package gr.papadogiannis.stefanos.reducers.impl;

import gr.papadogiannis.stefanos.constants.ApplicationConstants;
import gr.papadogiannis.stefanos.reducers.ReduceWorker;
import gr.papadogiannis.stefanos.models.GeoPointPair;
import com.google.maps.model.DirectionsResult;

import java.util.concurrent.CountDownLatch;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.util.logging.Logger;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public final class ReduceWorkerImpl implements ReduceWorker {

    private static final Logger LOGGER = Logger.getLogger(ReduceWorkerImpl.class.getName());

    private static final String REDUCER_WORKER_IS_WAITING_FOR_TASKS_MESSAGE =
            "ReducerWorker is waiting for tasks at port %d...";
    private static final String SERVER_SOCKET_ON_REDUCE_WORKER_WAS_CLOSED_ERROR_MESSAGE =
            "Server socket on reduce worker was closed.";
    private static final String REDUCE_WORKER_IS_EXITING_MESSAGE = "ReduceWorker %d is exiting...";
    private static final String REDUCE_WORKER_WAS_CREATED_MESSAGE = "ReduceWorker was created.";

    private static final Map<GeoPointPair, List<DirectionsResult>> mapToReturn = new HashMap<>();
    private CountDownLatch countDownLatch = new CountDownLatch(4);
    private boolean isNotFinished = true;
    private ServerSocket serverSocket;
    private final int port;

    public ReduceWorkerImpl(int port) {
        LOGGER.info(REDUCE_WORKER_WAS_CREATED_MESSAGE);
        this.port = port;
    }

    public static void main(String[] args) {
        final ReduceWorkerImpl reduceWorker = new ReduceWorkerImpl(Integer.parseInt(args[0]));
        reduceWorker.run();
    }

    @Override
    public void waitForMasterAck() {
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    public void setCountDownLatch(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
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
        LOGGER.info(String.format(REDUCER_WORKER_IS_WAITING_FOR_TASKS_MESSAGE, port));
        try {
            serverSocket = new ServerSocket(port);
            ReduceWorkerImpl reduceWorker = this;
            while (isNotFinished) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    new Thread(new ReduceWorkerRunnable(socket, serverSocket, reduceWorker)).start();
                } catch (SocketException ex) {
                    LOGGER.severe(SERVER_SOCKET_ON_REDUCE_WORKER_WAS_CLOSED_ERROR_MESSAGE);
                }
            }
        } catch (IOException ioException) {
            LOGGER.severe(ioException.toString());
        } finally {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException ioException) {
                LOGGER.severe(ioException.toString());
            }
        }
    }

    @Override
    public void waitForTasksThread() {
    }

    public static void clearMapToReturn() {
        mapToReturn.clear();
    }

    public void run() {
        initialize();
        LOGGER.info(String.format(REDUCE_WORKER_IS_EXITING_MESSAGE, port));
    }

    private static class ReduceWorkerRunnable implements Runnable {

        private static final String SENDING_RESULT_TO_MASTER_MESSAGE = "Sending result %s to master from %d...";
        private static final String RECEIVED_MESSAGE = "%d received %s";

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
            } catch (IOException ioException) {
                LOGGER.severe(ioException.toString());
            }
        }

        void waitForTasksThread() {
            Object incomingObject;
            try {
                while (isNotFinished) {
                    incomingObject = objectInputStream.readObject();
                    if (incomingObject instanceof String) {
                        final String inputLine = (String) incomingObject;
                        LOGGER.info(String.format(RECEIVED_MESSAGE, socket.getPort(), inputLine));
                        final CountDownLatch countDownLatch = reduceWorker.getCountDownLatch();
                        switch (inputLine) {
                            case ApplicationConstants.ACK_SIGNAL:
                                countDownLatch.await();
                                sendResults();
                                reduceWorker.setCountDownLatch(new CountDownLatch(4));
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
                        LOGGER.info(String.format(RECEIVED_MESSAGE, socket.getPort(), incoming));
                    }
                }
            } catch (IOException | ClassNotFoundException | InterruptedException exception) {
                LOGGER.severe(exception.toString());
            } finally {
                isNotFinished = false;
                try {
                    if (objectInputStream != null)
                        objectInputStream.close();
//                    if (objectOutputStream != null)
//                        objectOutputStream.close();
                    if (socket != null)
                        socket.close();
                } catch (IOException ioException) {
                    LOGGER.severe(ioException.toString());
                }
            }
        }

        void sendResults() {
            LOGGER.info(String.format(SENDING_RESULT_TO_MASTER_MESSAGE, mapToReturn, socket.getPort()));
            try {
                objectOutputStream.writeObject(mapToReturn);
                objectOutputStream.flush();
            } catch (IOException ioException) {
                LOGGER.severe(ioException.toString());
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
