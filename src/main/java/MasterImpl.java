import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.LatLng;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import javafx.util.Pair;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.Scanner;

public class MasterImpl implements Master {

    private final MemCache memCache = new MemCache();
    private final Scanner input = new Scanner(System.in);
    private ObjectOutputStream objectOutputStreamToAthens, objectOutputStreamToJamaica, objectOutputStreamToHavana,
                               objectOutputStreamToSaoPaolo, objectOutputStreamToMoscow;
    private ObjectInputStream objectInputStreamFromAthens, objectInputStreamFromJamaica, objectInputStreamFromHavana,
                              objectInputStreamFromSaoPaolo, objectInputStreamFromMoscow;

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
                final double truncatedStartLatitude = roundTo2Decimals(startLatitude);
                final double truncatedStartLongitude = roundTo2Decimals(startLongitude);
                final double truncatedEndLatitude = roundTo2Decimals(endLatitude);
                final double truncatedEndLongitude = roundTo2Decimals(endLongitude);
                System.out.println(String.format(
                        "You asked directions between : (%f, %f), (%f, %f)",
                        truncatedStartLatitude, truncatedStartLongitude,
                        truncatedEndLatitude, truncatedEndLongitude));
                searchCache(new GeoPoint(truncatedStartLatitude, truncatedStartLongitude),
                            new GeoPoint(truncatedEndLatitude, truncatedEndLongitude));
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
    public Directions searchCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        final Directions memCachedDirections = memCache.getDirections(new Pair<>(startGeoPoint, endGeoPoint));
        if (memCachedDirections == null) {
            distributeToMappers(startGeoPoint, endGeoPoint);
            final Directions googleDirectionsAPI = askGoogleDirectionsAPI(startGeoPoint, endGeoPoint);
            return null;
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
    }

    @Override
    public void collectDataFromReducer() {

    }

    @Override
    public Directions askGoogleDirectionsAPI(GeoPoint startGeoPoint, GeoPoint endGeoPoint) {
        GeoApiContext geoApiContext = new GeoApiContext();
        geoApiContext.setApiKey(ApplicationConstants.DIRECTIONS_API_KEY);
        try {
            final DirectionsResult directionsResult = DirectionsApi.newRequest(geoApiContext).origin(new LatLng(startGeoPoint.getLatitude(),
                    startGeoPoint.getLongitude())).destination(new LatLng(endGeoPoint.getLatitude(),
                    endGeoPoint.getLongitude())).await();
            updateDatabase(startGeoPoint, endGeoPoint, directionsResult);

        } catch (ApiException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean updateCache(GeoPoint startGeoPoint, GeoPoint endGeoPoint, Directions directions) {
        memCache.insertDirections(new Pair<>(startGeoPoint, endGeoPoint), directions);
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
