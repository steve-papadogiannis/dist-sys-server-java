package gr.papadogiannis.stefanos.masters.impl;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import gr.papadogiannis.stefanos.constants.ApplicationConstants;
import gr.papadogiannis.stefanos.models.DirectionsResultWrapper;
import gr.papadogiannis.stefanos.reducers.impl.ReduceWorkerImpl;
import gr.papadogiannis.stefanos.models.GeoPointPair;
import gr.papadogiannis.stefanos.caches.MemCache;
import gr.papadogiannis.stefanos.models.GeoPoint;
import gr.papadogiannis.stefanos.masters.Master;
import gr.papadogiannis.stefanos.models.MapTask;
import com.google.maps.model.DirectionsResult;
import com.google.maps.errors.ApiException;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.LatLng;
import org.bson.UuidRepresentation;
import org.mongojack.JacksonMongoCollection;

import java.io.ObjectOutputStream;


import java.io.ObjectInputStream;
import java.util.logging.Logger;


import java.io.IOException;


import java.net.Socket;


import java.util.*;

/**
 * @author Stefanos Papadogiannis
 * <p>
 * Created on 15/4/2017
 */
public class MasterImpl implements Master {

    private static final Logger LOGGER = Logger.getLogger(MasterImpl.class.getName());

    private static final String QUERIED_DIRECTIONS_WERE_FETCHED_FROM_MEM_CACHE_MESSAGE =
            "Master: Queried Directions were fetched from MemCache";
    private static final String A_WORKER_HAD_THE_DIRECTIONS_ISSUED_MESSAGE =
            "Master: A worker had the directions issued";
    private static final String INVOCATION_OF_GOOGLE_API_MESSAGE = "Master: I invoke google API for directions!";
    private static final String SENDING_TO_MAP_WORKER_MESSAGE = "Sending %s to map worker %s...";
    private static final String SOMETHING_WENT_WRONG_ON_ACKNOWLEDGEMENT_ERROR_MESSAGE =
            "Something went wrong on acknowledgement";
    public static final String SENDING_ACK_TO_REDUCE_WORKER_MESSAGE = "Sending ack to reduce worker %s... %n";

    private final List<Node> reducerNodes = new ArrayList<>();
    private final List<Node> mapperNodes = new ArrayList<>();
    private final Scanner input = new Scanner(System.in);
    private DirectionsResult resultOfMapReduce = null;
    private final MemCache memCache = new MemCache();

    public MasterImpl(String[] args) {
        int i = 1;
        for (; i < args.length - 2; i += 2) {
            final Node node = new MapperNode(Integer.parseInt(args[i + 1]), args[i]);
            mapperNodes.add(node);
        }
        final Node node = new ReducerNode(Integer.parseInt(args[i + 1]), args[i]);
        reducerNodes.add(node);
    }

    public double getStartLatitude() {
        return startLatitude;
    }

    public void setStartLatitude(double startLatitude) {
        this.startLatitude = startLatitude;
    }

    public double getStartLongitude() {
        return startLongitude;
    }

    public void setStartLongitude(double startLongitude) {
        this.startLongitude = startLongitude;
    }

    public double getEndLatitude() {
        return endLatitude;
    }

    public void setEndLatitude(double endLatitude) {
        this.endLatitude = endLatitude;
    }

    public double getEndLongitude() {
        return endLongitude;
    }

    public void setEndLongitude(double endLongitude) {
        this.endLongitude = endLongitude;
    }

    private double startLatitude;
    private double startLongitude;
    private double endLatitude;
    private double endLongitude;

    @Override
    public void initialize() {
        for (Node node : mapperNodes)
            node.openSocket();
        for (Node node : reducerNodes)
            node.openSocket();
    }

    public void waitForNewQueriesThread() {
        int selection = 0;
        while (selection != 2) {
            LOGGER.info("Choose from these choices");
            LOGGER.info("-------------------------");
            LOGGER.info("1 - Enter geo points");
            LOGGER.info("2 - Quit");
            selection = input.nextInt();
            if (selection == 1) {
                LOGGER.info("Enter your starting point latitude: ");
                startLatitude = input.nextDouble();
                LOGGER.info("Enter your starting point longitude: ");
                startLongitude = input.nextDouble();
                LOGGER.info("Enter your end point latitude: ");
                endLatitude = input.nextDouble();
                LOGGER.info("Enter your end point longitude: ");
                endLongitude = input.nextDouble();
                LOGGER.info(String.format(
                        "You asked directions between : (%f, %f), (%f, %f)",
                        startLatitude, startLongitude,
                        endLatitude, endLongitude));
                LOGGER.info(searchCache(new GeoPoint(startLatitude, startLongitude),
                        new GeoPoint(endLatitude, endLongitude)).toString());
            }
        }
        tearDownApplication();
    }

    public void tearDownApplication() {
        for (Node node : mapperNodes) {
            node.closeOutputStream();
            node.closeInputStream();
            node.closeSocket();
        }
        for (Node node : reducerNodes) {
            node.closeOutputStream();
            node.closeInputStream();
            node.closeSocket();
        }
    }

    @Override
    public DirectionsResult searchCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        final DirectionsResult memCachedDirections = memCache.getDirections(new GeoPointPair(startGeoPoint, endGeoPoint));
        if (memCachedDirections == null) {
            distributeToMappers(startGeoPoint, endGeoPoint);
            if (resultOfMapReduce == null) {
                LOGGER.info(INVOCATION_OF_GOOGLE_API_MESSAGE);
                final DirectionsResult googleDirectionsAPI = askGoogleDirectionsAPI(startGeoPoint, endGeoPoint);
                updateCache(startGeoPoint, endGeoPoint, googleDirectionsAPI);
                updateDatabase(startGeoPoint, endGeoPoint, googleDirectionsAPI);
                return googleDirectionsAPI;
            } else {
                LOGGER.info(A_WORKER_HAD_THE_DIRECTIONS_ISSUED_MESSAGE);
                return resultOfMapReduce;
            }
        } else {
            LOGGER.info(QUERIED_DIRECTIONS_WERE_FETCHED_FROM_MEM_CACHE_MESSAGE);
            return memCachedDirections;
        }
    }

    @Override
    public void distributeToMappers(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        final MapTask mapTask = new MapTask(startGeoPoint, endGeoPoint);
        for (Node node : mapperNodes) {
            LOGGER.info(String.format(SENDING_TO_MAP_WORKER_MESSAGE, mapTask, node));
            node.sendMapTask(mapTask);
        }
        waitForMappers();
    }

    @Override
    public void waitForMappers() {
        final List<String> acknowledgements = new ArrayList<>();
        for (Node node : mapperNodes) {
            final String acknowledgement = node.getAcknowledgement();
            if (acknowledgement != null) {
                acknowledgements.add(acknowledgement);
            }
        }
        if (acknowledgements.size() == 4 &&
                acknowledgements.stream().allMatch(x -> x.equals(ApplicationConstants.ACK_SIGNAL))) {
            ackToReducers();
        } else {
            LOGGER.info(SOMETHING_WENT_WRONG_ON_ACKNOWLEDGEMENT_ERROR_MESSAGE);
        }
    }

    @Override
    public void ackToReducers() {
        for (Node node : reducerNodes) {
            LOGGER.info(String.format(SENDING_ACK_TO_REDUCE_WORKER_MESSAGE, node));
            node.sendAck();
        }
        collectDataFromReducer();
    }

    @Override
    public void collectDataFromReducer() {
        Map<GeoPointPair, List<DirectionsResult>> result = null;
        for (Node node : reducerNodes) {
            result = node.getResult();
        }
        ReduceWorkerImpl.clearMapToReturn();
        if (result != null) {
            resultOfMapReduce = calculateEuclideanMin(result);
        }
    }

    private DirectionsResult calculateEuclideanMin(Map<GeoPointPair, List<DirectionsResult>> result) {
        if (result.isEmpty()) {
            return null;
        } else {
            Map.Entry<GeoPointPair, List<DirectionsResult>> min = null;
            final GeoPoint startGeoPoint = new GeoPoint(startLatitude, startLongitude);
            final GeoPoint endGeoPoint = new GeoPoint(endLatitude, endLongitude);
            for (Map.Entry<GeoPointPair, List<DirectionsResult>> entry : result.entrySet()) {
                if (min == null ||
                        min.getKey().getStartGeoPoint().euclideanDistance(startGeoPoint) +
                                min.getKey().getEndGeoPoint().euclideanDistance(endGeoPoint) >
                                entry.getKey().getStartGeoPoint().euclideanDistance(startGeoPoint) +
                                        entry.getKey().getEndGeoPoint().euclideanDistance(endGeoPoint)) {
                    min = entry;
                }
            }
            final List<DirectionsResult> minDirectionsResultList = min != null ? min.getValue() : null;
            if (minDirectionsResultList != null && !minDirectionsResultList.isEmpty()) {
                DirectionsResult minDirectionsResult = null;
                for (DirectionsResult directionsResult : minDirectionsResultList) {
                    if (minDirectionsResult == null) {
                        minDirectionsResult = directionsResult;
                    } else {
                        final long[] totalDurationOfMin = {0};
                        final long[] totalDurationOfIteratee = {0};
                        Arrays.stream(minDirectionsResult.routes).forEach(x -> {
                            Arrays.stream(x.legs).forEach(y -> {
                                totalDurationOfMin[0] += y.duration.inSeconds;
                            });
                        });
                        Arrays.stream(directionsResult.routes).forEach(x -> {
                            Arrays.stream(x.legs).forEach(y -> {
                                totalDurationOfIteratee[0] += y.duration.inSeconds;
                            });
                        });
                        if (totalDurationOfIteratee[0] < totalDurationOfMin[0]) {
                            minDirectionsResult = directionsResult;
                        }
                    }
                }
                return minDirectionsResult;
            } else {
                return null;
            }
        }
    }

    @Override
    public DirectionsResult askGoogleDirectionsAPI(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        GeoApiContext geoApiContext = new GeoApiContext();
        geoApiContext.setApiKey(ApplicationConstants.DIRECTIONS_API_KEY);
        DirectionsResult directionsResult = null;
        try {
            directionsResult = DirectionsApi.newRequest(geoApiContext).origin(new LatLng(startGeoPoint.getLatitude(),
                    startGeoPoint.getLongitude())).destination(new LatLng(endGeoPoint.getLatitude(),
                    endGeoPoint.getLongitude())).await();
        } catch (ApiException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return directionsResult;
    }

    @Override
    public void updateCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions) {
        memCache.insertDirections(new GeoPointPair(startGeoPoint, endGeoPoint), directions);
    }

    @Override
    public void updateDatabase(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions) {
        final MongoClientSettings mongoClientSettings = MongoClientSettings
                .builder()
                .applyConnectionString(
                        new ConnectionString(
                                "mongodb://root:example@localhost:27017"
                        )).build();
        final MongoClient mongoClient = MongoClients.create(mongoClientSettings);
        final JacksonMongoCollection<DirectionsResultWrapper> collection = JacksonMongoCollection.builder()
                .build(mongoClient, "dist-sys", "directions", DirectionsResultWrapper.class, UuidRepresentation.STANDARD);
        collection.insert(new DirectionsResultWrapper(startGeoPoint,
                endGeoPoint, directions));
    }

    private static class Node {

        public static final String TO_STRING_MESSAGE = "[ %s : %d ]";

        @Override
        public String toString() {
            return String.format(TO_STRING_MESSAGE, ip, port);
        }

        private final int port;

        public Socket getSocket() {
            return socket;
        }

        ObjectOutputStream getObjectOutputStream() {
            return objectOutputStream;
        }

        public ObjectInputStream getObjectInputStream() {
            return objectInputStream;
        }

        private Socket socket;
        private ObjectOutputStream objectOutputStream;
        private ObjectInputStream objectInputStream;

        public String getIp() {
            return ip;
        }

        private final String ip;

        private Node(int port, String ip) {
            this.port = port;
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        void openSocket() {
            try {
                socket = new Socket(ip, port);
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectInputStream = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void closeOutputStream() {
            try {
                objectOutputStream.writeObject(ApplicationConstants.EXIT_SIGNAL);
                objectOutputStream.flush();
                if (objectOutputStream != null)
                    objectOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void closeInputStream() {
            try {
                if (objectInputStream != null)
                    objectInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void closeSocket() {
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void sendMapTask(MapTask mapTask) {
            try {
                objectOutputStream.writeObject(mapTask);
                objectOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String getAcknowledgement() {
            try {
                return (String) objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }

        void sendAck() {
            try {
                objectOutputStream.writeObject(ApplicationConstants.ACK_SIGNAL);
                objectOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<GeoPointPair, List<DirectionsResult>> getResult() {
            try {
                return (Map<GeoPointPair, List<DirectionsResult>>) objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static class MapperNode extends Node {

        private MapperNode(int port, String ip) {
            super(port, ip);
        }

    }

    private static class ReducerNode extends Node {

        private ReducerNode(int port, String ip) {
            super(port, ip);
        }

        @Override
        void closeOutputStream() {
            try {
                getObjectOutputStream().writeObject(ApplicationConstants.TERMINATE_SIGNAL);
                getObjectOutputStream().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
