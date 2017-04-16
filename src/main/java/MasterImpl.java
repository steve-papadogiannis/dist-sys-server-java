import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.LatLng;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class MasterImpl implements Master {

    private final MemCache memCache = new MemCache();
    private final Scanner input = new Scanner(System.in);
    private ObjectOutputStream objectOutputStreamToAthens, objectOutputStreamToJamaica, objectOutputStreamToHavana,
                               objectOutputStreamToSaoPaolo, objectOutputStreamToMoscow;
    private ObjectInputStream objectInputStreamFromAthens, objectInputStreamFromJamaica, objectInputStreamFromHavana,
                              objectInputStreamFromSaoPaolo, objectInputStreamFromMoscow;
    private DirectionsResult resultOfMapReduce = null;
    private double truncatedStartLatitude = 38.06, truncatedStartLongitude = 23.80, truncatedEndLatitude = 38.04,
        truncatedEndLongitude = 23.80;

    @Override
    public void initialize() {
        new Thread(new MapWorkerImpl(ApplicationConstants.ATHENS, ApplicationConstants.ATHENS_PORT)).start();
        new Thread(new MapWorkerImpl(ApplicationConstants.JAMAICA, ApplicationConstants.JAMAICA_PORT)).start();
        new Thread(new MapWorkerImpl(ApplicationConstants.HAVANA, ApplicationConstants.HAVANA_PORT)).start();
        new Thread(new MapWorkerImpl(ApplicationConstants.SAO_PAOLO, ApplicationConstants.SAO_PAOLO_PORT)).start();
        new Thread(new ReduceWorkerImpl(ApplicationConstants.MOSCOW, ApplicationConstants.MOSCOW_PORT)).start();
        waitForNewQueriesThread();
    }

    @Override
    public void waitForNewQueriesThread() {
        int selection = 0;
        while (selection != 2) {
            System.out.println("Choose from these choices");
            System.out.println("-------------------------");
            System.out.println("1 - Enter geo points");
            System.out.println("2 - Quit");
            selection = input.nextInt();
            if (selection == 1) {
                System.out.print("Enter your starting point latitude: ");
                final double startLatitude = input.nextDouble();
                System.out.print("Enter your starting point longitude: ");
                final double startLongitude = input.nextDouble();
                System.out.print("Enter your end point latitude: ");
                final double endLatitude = input.nextDouble();
                System.out.print("Enter your end point longitude: ");
                final double endLongitude = input.nextDouble();
                truncatedStartLatitude = roundTo2Decimals(startLatitude);
                truncatedStartLongitude = roundTo2Decimals(startLongitude);
                truncatedEndLatitude = roundTo2Decimals(endLatitude);
                truncatedEndLongitude = roundTo2Decimals(endLongitude);
                System.out.println(String.format(
                        "You asked directions between : (%f, %f), (%f, %f)",
                        truncatedStartLatitude, truncatedStartLongitude,
                        truncatedEndLatitude, truncatedEndLongitude));
                System.out.println(searchCache(new GeoPoint(truncatedStartLatitude, truncatedStartLongitude),
                            new GeoPoint(truncatedEndLatitude, truncatedEndLongitude)));
            }
        }
        tearDownApplication();
    }

    private double roundTo2Decimals(double val) {
        DecimalFormat decimalFormat = new DecimalFormat("###.##");
        return Double.valueOf(decimalFormat.format(val));
    }

    private void tearDownApplication() {
        System.exit(1);
    }

    @Override
    public DirectionsResult searchCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        final DirectionsResult memCachedDirections = memCache.getDirections(new GeoPointPair(startGeoPoint, endGeoPoint));
        if (memCachedDirections == null) {
            distributeToMappers(startGeoPoint, endGeoPoint);
            if (resultOfMapReduce == null) {
                final DirectionsResult googleDirectionsAPI = askGoogleDirectionsAPI(startGeoPoint, endGeoPoint);
                updateCache(startGeoPoint, endGeoPoint, googleDirectionsAPI);
                updateDatabase(startGeoPoint, endGeoPoint, googleDirectionsAPI);
                return googleDirectionsAPI;
            } else {
                return resultOfMapReduce;
            }
        } else {
            return memCachedDirections;
        }
    }

    @Override
    public void distributeToMappers(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        final MapTask mapTask = new MapTask(startGeoPoint, endGeoPoint);
        System.out.println("Sending " + mapTask + " to map worker " + ApplicationConstants.ATHENS + " ... ");
        try {
            if (objectOutputStreamToAthens == null) {
                openSocket(ApplicationConstants.ATHENS_PORT);
            }
            objectOutputStreamToAthens.writeObject(mapTask);
            objectOutputStreamToAthens.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Sending " + mapTask + " to map worker " + ApplicationConstants.JAMAICA + " ... ");
        try {
            if (objectOutputStreamToJamaica == null) {
                openSocket(ApplicationConstants.JAMAICA_PORT);
            }
            objectOutputStreamToJamaica.writeObject(mapTask);
            objectOutputStreamToJamaica.flush();
        } catch (IOException  e) {
            e.printStackTrace();
        }
        System.out.println("Sending " + mapTask + " to map worker " + ApplicationConstants.HAVANA + " ... ");
        try {
            if (objectOutputStreamToHavana == null) {
                openSocket(ApplicationConstants.HAVANA_PORT);
            }
            objectOutputStreamToHavana.writeObject(mapTask);
            objectOutputStreamToHavana.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Sending " + mapTask + " to map worker " + ApplicationConstants.SAO_PAOLO + " ... ");
        try {
            if (objectOutputStreamToSaoPaolo == null) {
                openSocket(ApplicationConstants.SAO_PAOLO_PORT);
            }
            objectOutputStreamToSaoPaolo.writeObject(mapTask);
            objectOutputStreamToSaoPaolo.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        waitForMappers();
    }

    private void openSocket(int port) {
        try {
            final Socket serverSocket = new Socket(ApplicationConstants.LOCALHOST, port);
            switch (port) {
                case ApplicationConstants.ATHENS_PORT:
                    objectOutputStreamToAthens = new ObjectOutputStream(serverSocket.getOutputStream());
                    objectInputStreamFromAthens = new ObjectInputStream(serverSocket.getInputStream());
                    break;
                case ApplicationConstants.JAMAICA_PORT:
                    objectOutputStreamToJamaica = new ObjectOutputStream(serverSocket.getOutputStream());
                    objectInputStreamFromJamaica = new ObjectInputStream(serverSocket.getInputStream());
                    break;
                case ApplicationConstants.HAVANA_PORT:
                    objectOutputStreamToHavana = new ObjectOutputStream(serverSocket.getOutputStream());
                    objectInputStreamFromHavana = new ObjectInputStream(serverSocket.getInputStream());
                    break;
                case ApplicationConstants.SAO_PAOLO_PORT:
                    objectOutputStreamToSaoPaolo = new ObjectOutputStream(serverSocket.getOutputStream());
                    objectInputStreamFromSaoPaolo = new ObjectInputStream(serverSocket.getInputStream());
                    break;
                case ApplicationConstants.MOSCOW_PORT:
                    objectOutputStreamToMoscow = new ObjectOutputStream(serverSocket.getOutputStream());
                    objectInputStreamFromMoscow = new ObjectInputStream(serverSocket.getInputStream());
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void waitForMappers() {
        try {
            final String acknowledgementFromAthens = (String) objectInputStreamFromAthens.readObject();
            final String acknowledgementFromJamaica = (String) objectInputStreamFromJamaica.readObject();
            final String acknowledgementFromHavana = (String) objectInputStreamFromHavana.readObject();
            final String acknowledgementFromSaoPaolo = (String) objectInputStreamFromSaoPaolo.readObject();
            if (acknowledgementFromAthens.equals("ack") &&
                acknowledgementFromJamaica.equals("ack") &&
                acknowledgementFromHavana.equals("ack") &&
                acknowledgementFromSaoPaolo.equals("ack")) {
                ackToReducers();
            } else {
                System.out.println("Something went wrong on acknowledgement");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ackToReducers() {
        System.out.println("Sending ack to reduce worker " + ApplicationConstants.MOSCOW + " ... ");
        try {
            if (objectOutputStreamToMoscow == null) {
                openSocket(ApplicationConstants.MOSCOW_PORT);
            }
            objectOutputStreamToMoscow.writeObject("ack");
            objectOutputStreamToMoscow.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        collectDataFromReducer();
    }

    @Override
    public void collectDataFromReducer() {
        Map<GeoPointPair, List<DirectionsResult>> result = null;
        try {
            result = (Map<GeoPointPair, List<DirectionsResult>>) objectInputStreamFromMoscow.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        if (result != null) {
            resultOfMapReduce = calculateEuclideanMin(result);
        }
    }

    private DirectionsResult calculateEuclideanMin(Map<GeoPointPair, List<DirectionsResult>> result) {
        if (result.isEmpty()) {
            return null;
        } else {
            Map.Entry<GeoPointPair, List<DirectionsResult>> min = null;
            final GeoPoint startGeoPoint = new GeoPoint(truncatedStartLatitude, truncatedStartLongitude);
            final GeoPoint endGeoPoint = new GeoPoint(truncatedEndLatitude, truncatedEndLongitude);
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
    public boolean updateCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions) {
        memCache.insertDirections(new GeoPointPair(startGeoPoint, endGeoPoint), directions);
        return true;
    }

    @Override
    public boolean updateDatabase(GeoPoint startGeoPoint, GeoPoint endGeoPoint, DirectionsResult directions) {
        final Mongo mongo = new Mongo("127.0.0.1", 27017);
        final DB db = mongo.getDB("local");
        final DBCollection dbCollection = db.getCollection("directions");
        final JacksonDBCollection<DirectionsResultWrapper, String> coll = JacksonDBCollection.wrap(dbCollection,
                DirectionsResultWrapper.class, String.class);
        final WriteResult<DirectionsResultWrapper, String> resultWrappers = coll.insert(new DirectionsResultWrapper(startGeoPoint,
                endGeoPoint, directions));
        return true;
    }

}
