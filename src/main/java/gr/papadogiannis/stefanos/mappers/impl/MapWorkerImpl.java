package gr.papadogiannis.stefanos.mappers.impl;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import gr.papadogiannis.stefanos.constants.ApplicationConstants;
import gr.papadogiannis.stefanos.models.DirectionsResultWrapper;
import gr.papadogiannis.stefanos.models.GeoPointPair;
import gr.papadogiannis.stefanos.mappers.MapWorker;
import gr.papadogiannis.stefanos.models.GeoPoint;
import gr.papadogiannis.stefanos.models.MapTask;
import com.google.maps.model.DirectionsResult;
import org.codehaus.jackson.map.ObjectMapper;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;

import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.security.MessageDigest;
import java.util.logging.Logger;
import java.text.DecimalFormat;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.io.*;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public class MapWorkerImpl implements MapWorker {

    private static final Logger LOGGER = Logger.getLogger(MapWorkerImpl.class.getName());

    private static final String SENDING_TO_REDUCE_WORKER_MESSAGE = "%d is sending %s to reduce worker [ %s : %d ]...";
    private static final String MAP_WORKER_IS_WAITING_MESSAGE = "MapWorker is waiting for tasks at port %d...";
    private static final String MAP_WORKER_IS_EXITING_MESSAGE = "MapWorker %d is exiting...";
    private static final String MAP_WORKER_WAS_CREATED_MESSAGE = "MapWorker was created.";
    private static final String RECEIVED_MESSAGE = "%d received %s";
    private static final String DECIMAL_FORMAT_PATTERN = "###.##";
    private static final String MD_5_ALGORITHM = "MD5";

    private ObjectOutputStream objectOutputStreamToReducer;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;
    private boolean isNotFinished = true;
    private final int port, reducerPort;
    private ServerSocket serverSocket;
    private final String reducerIp;
    private Socket socketToReducer;
    private Socket socket;

    private MapWorkerImpl(int port, String reducerIp, int reducerPort) {
        LOGGER.info(MAP_WORKER_WAS_CREATED_MESSAGE);
        this.reducerPort = reducerPort;
        this.reducerIp = reducerIp;
        this.port = port;
    }

    public static void main(String[] args) {
        final MapWorkerImpl mapWorker = new MapWorkerImpl(
                Integer.parseInt(args[0]),
                args[1],
                Integer.parseInt(args[2]));
        mapWorker.run();
    }

    private void run() {
        initialize();
        if (objectOutputStreamToReducer != null)
            try {
                objectOutputStreamToReducer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        if (socketToReducer != null)
            try {
                socketToReducer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        LOGGER.info(String.format(MAP_WORKER_IS_EXITING_MESSAGE, port));
    }

    @Override
    public void initialize() {
        LOGGER.info(String.format(MAP_WORKER_IS_WAITING_MESSAGE, port));
        try {
            serverSocket = new ServerSocket(port);
            while (isNotFinished) {
                try {
                    socket = serverSocket.accept();
                    objectInputStream = new ObjectInputStream(socket.getInputStream());
                    objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                    waitForTasksThread();
                } catch (IOException ioException) {
                    LOGGER.severe(ioException.toString());
                }
            }
        } catch (IOException ioException) {
            LOGGER.severe(ioException.toString());
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
            } catch (IOException ioException) {
                LOGGER.severe(ioException.toString());
            }
        }
    }

    @Override
    public void waitForTasksThread() {
        Object incoming;
        try {
            while (isNotFinished) {
                incoming = objectInputStream.readObject();
                LOGGER.info(String.format(RECEIVED_MESSAGE, port, incoming));
                if (incoming instanceof MapTask) {
                    final MapTask mapTask = (MapTask) incoming;
                    final List<Map<GeoPointPair, DirectionsResult>> map =
                            map(mapTask.getStartGeoPoint(), mapTask.getEndGeoPoint());
                    sendToReducers(map);
                    notifyMaster();
                } else if (incoming instanceof String) {
                    if (incoming.equals(ApplicationConstants.EXIT_SIGNAL)) {
                        isNotFinished = false;
                        objectInputStream.close();
                        objectOutputStream.close();
                        socket.close();
                        serverSocket.close();
                    }
                }

            }
        } catch (IOException | ClassNotFoundException exception) {
            LOGGER.severe(exception.toString());
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
            } catch (IOException ioException) {
                LOGGER.severe(ioException.toString());
            }
        }
    }

    @Override
    public List<Map<GeoPointPair, DirectionsResult>> map(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        final Mongo mongo = new Mongo("127.0.0.1", 27017);
        final DB db = mongo.getDB("local");
        final DBCollection dbCollection = db.getCollection("directions");
        final JacksonDBCollection<DirectionsResultWrapper, String> coll = JacksonDBCollection.wrap(dbCollection,
            DirectionsResultWrapper.class, String.class);
        final DBCursor<DirectionsResultWrapper> cursor = coll.find();
        final List<DirectionsResultWrapper> list = new ArrayList<>();
//        final ObjectMapper mapper = new ObjectMapper();
        while (cursor.hasNext()) {
           list.add(cursor.next());
        }
//        String filename = port + "_directions";
//        File file = new File(filename);
//        if (file.exists() && !file.isDirectory()) {
//            try {
//                final FileReader fr = new FileReader(filename);
//                final BufferedReader in = new BufferedReader(fr);
//                String line = null;
//                while ((line = in.readLine()) != null) {
//                    list.add(mapper.readValue(line, DirectionsResultWrapper.class));
//                }
//                in.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        final long ipPortHash = calculateHash("127.0.0.1" + socket.getLocalPort());
        final long ipPortHashMod4 = ipPortHash % 4 < 0 ? (-(ipPortHash % 4)) : ipPortHash % 4;
        final List<DirectionsResultWrapper> resultsThatThisWorkerIsInChargeOf = list.stream()
                .filter(x -> {
                    final long geoPointsHash = calculateHash(String.valueOf(x.getStartPoint().getLatitude()) +
                            String.valueOf(x.getStartPoint().getLongitude()) +
                            String.valueOf(x.getEndPoint().getLatitude()) +
                            String.valueOf(x.getEndPoint().getLongitude()));
                    final long geoPointsHashMod4 = geoPointsHash % 4 < 0 ? (-(geoPointsHash % 4)) : geoPointsHash % 4;
                    return ipPortHashMod4 == geoPointsHashMod4;
                }).collect(Collectors.toList());
        final List<Map<GeoPointPair, DirectionsResult>> result = new ArrayList<>();
        resultsThatThisWorkerIsInChargeOf.forEach(x -> {
            final Map<GeoPointPair, DirectionsResult> map = new HashMap<>();
            final boolean isStartLatitudeNearIssuedStartLatitude = Math.abs(
                    startGeoPoint.getLatitude() - x.getStartPoint().getLatitude()) < 0.0001;
            final boolean isStartLongitudeNearIssuedStartLongitude = Math.abs(
                    startGeoPoint.getLongitude() - x.getStartPoint().getLongitude()) < 0.0001;
            final boolean isEndLatitudeNearIssuedEndLatitude = Math.abs(
                    endGeoPoint.getLatitude() - x.getEndPoint().getLatitude()) < 0.0001;
            final boolean isEndLongitudeNearIssuedEndLongitude = Math.abs(
                    endGeoPoint.getLongitude() - x.getEndPoint().getLongitude()) < 0.0001;
            if (isStartLatitudeNearIssuedStartLatitude &&
                    isStartLongitudeNearIssuedStartLongitude &&
                    isEndLatitudeNearIssuedEndLatitude &&
                    isEndLongitudeNearIssuedEndLongitude) {
                final GeoPointPair geoPointPair = new GeoPointPair(
                        new GeoPoint(
                                roundTo2Decimals(x.getStartPoint().getLatitude()),
                                roundTo2Decimals(x.getStartPoint().getLongitude())),
                        new GeoPoint(
                                roundTo2Decimals(x.getEndPoint().getLatitude()),
                                roundTo2Decimals(x.getEndPoint().getLongitude())
                        ));
                map.put(geoPointPair, x.getDirectionsResult());
                result.add(map);
            }
        });
        return result;
    }

    @Override
    public void notifyMaster() {
        try {
            objectOutputStream.writeObject(ApplicationConstants.ACK_SIGNAL);
            objectOutputStream.flush();
        } catch (IOException ioException) {
            LOGGER.severe(ioException.toString());
        }
    }

    @Override
    public long calculateHash(String string) {
        byte[] bytesOfMessage;
        bytesOfMessage = string.getBytes(StandardCharsets.UTF_8);
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance(MD_5_ALGORITHM);
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            LOGGER.severe(noSuchAlgorithmException.toString());
        }
        byte[] theDigest = md != null ? md.digest(bytesOfMessage) : new byte[0];
        final ByteBuffer bb = ByteBuffer.wrap(theDigest);
        return bb.getLong();
    }

    @Override
    public void sendToReducers(List<Map<GeoPointPair, DirectionsResult>> map) {
        LOGGER.info(String.format(SENDING_TO_REDUCE_WORKER_MESSAGE, port, map, reducerIp, reducerPort));
        try {
            socketToReducer = new Socket(reducerIp, reducerPort);
            objectOutputStreamToReducer = new ObjectOutputStream(socketToReducer.getOutputStream());
            objectOutputStreamToReducer.writeObject(map);
            objectOutputStreamToReducer.flush();
            objectOutputStreamToReducer.writeObject(ApplicationConstants.EXIT_SIGNAL);
            objectOutputStreamToReducer.flush();
        } catch (IOException ioException) {
            LOGGER.severe(ioException.toString());
        }
    }

    private double roundTo2Decimals(double val) {
        final DecimalFormat decimalFormat = new DecimalFormat(DECIMAL_FORMAT_PATTERN);
        return Double.parseDouble(decimalFormat.format(val));
    }

}
