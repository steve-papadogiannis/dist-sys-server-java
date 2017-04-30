import com.google.maps.model.DirectionsResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MapWorkerImpl implements MapWorker{

    private final String name;
    private final int port;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;
    private Socket socket;
    private ObjectOutputStream objectOutputStreamToMoscow;
    private boolean isNotFinished = true;
    private ServerSocket serverSocket;
    private Socket socketToMoscow;

    private MapWorkerImpl(String name, int port) {
        System.out.println("MapWorker " + name + " was created.");
        this.name = name;
        this.port = port;
    }

    public static void main(String[] args) {
        MapWorkerImpl mapWorker = new MapWorkerImpl(args[0], Integer.parseInt(args[1]));
        mapWorker.run();
    }

    private void run() {
        initialize();
        if (objectOutputStreamToMoscow != null)
            try {
                objectOutputStreamToMoscow.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        if (socketToMoscow != null)
            try {
                socketToMoscow.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        System.out.println("MapWorker " + name + " is exiting...");
    }

    @Override
    public void initialize() {
        System.out.println("MapWorker " + name + " is waiting for tasks at port " + port + " ... ");
        try {
            serverSocket = new ServerSocket(port);
            while (isNotFinished) {
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
        Object incoming;
        try {
            while (isNotFinished) {
                incoming = objectInputStream.readObject();
                System.out.println(name + " received " + incoming);
                if (incoming instanceof MapTask) {
                    final MapTask mapTask = (MapTask) incoming;
                    final List<Map<GeoPointPair, DirectionsResult>> map =
                            map(mapTask.getStartGeopoint(), mapTask.getEndGeoPoint());
                    sendToReducers(map);
                    notifyMaster();
                } else if (incoming instanceof String) {
                    if (incoming.equals("exit")) {
                        isNotFinished = false;
                        objectInputStream.close();
                        objectOutputStream.close();
                        socket.close();
                        serverSocket.close();
                    }
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
    public List<Map<GeoPointPair, DirectionsResult>> map(GeoPoint obj1, GeoPoint obj2) {
        final Mongo mongo = new Mongo("127.0.0.1", 27017);
        final DB db = mongo.getDB("local");
        final DBCollection dbCollection = db.getCollection("directions");
        final JacksonDBCollection<DirectionsResultWrapper, String> coll = JacksonDBCollection.wrap(dbCollection,
            DirectionsResultWrapper.class, String.class);
        final DBCursor<DirectionsResultWrapper> cursor = coll.find();
        final List<DirectionsResultWrapper> list = new ArrayList<>();
        while (cursor.hasNext()) {
           list.add(cursor.next());
        }
        final long ipPortHash = calculateHash("127.0.0.1" + socket.getLocalPort());
        final long ipPortHashMod4 = ipPortHash % 4 < 0 ? (- (ipPortHash % 4)) : ipPortHash % 4;
        final List<DirectionsResultWrapper> resultsThatThisWorkerIsInChargeOf = list.stream()
            .filter(x -> {
                final long geoPointsHash = calculateHash(String.valueOf(x.getStartPoint().getLatitude()) +
                                                                String.valueOf(x.getStartPoint().getLongitude()) +
                                                                String.valueOf(x.getEndPoint().getLatitude()) +
                                                                String.valueOf(x.getEndPoint().getLongitude()));
                final long geoPointsHashMod4 = geoPointsHash % 4 < 0 ? (- (geoPointsHash % 4)) : geoPointsHash % 4;
                return ipPortHashMod4 == geoPointsHashMod4;
            }).collect(Collectors.toList());
        final List<Map<GeoPointPair, DirectionsResult>> result = new ArrayList<>();
        resultsThatThisWorkerIsInChargeOf.forEach(x -> {
            final Map<GeoPointPair, DirectionsResult> map = new HashMap<>();
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
        });
        return result;
    }

    @Override
    public void notifyMaster() {
        try {
            objectOutputStream.writeObject("ack");
            objectOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long calculateHash(String string) {
        byte[] bytesOfMessage = new byte[0];
        try {
            bytesOfMessage = string.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] thedigest = md != null ? md.digest(bytesOfMessage) : new byte[0];
        final ByteBuffer bb = ByteBuffer.wrap(thedigest);
        return bb.getLong();
    }

    @Override
    public void sendToReducers(List<Map<GeoPointPair, DirectionsResult>> map) {
        System.out.println(name + " is sending " + map + " to reduce worker " + ApplicationConstants.MOSCOW + " ... ");
        try {
            socketToMoscow = new Socket(ApplicationConstants.LOCALHOST, ApplicationConstants.MOSCOW_PORT);
            objectOutputStreamToMoscow = new ObjectOutputStream(socketToMoscow.getOutputStream());
            objectOutputStreamToMoscow.writeObject(map);
            objectOutputStreamToMoscow.flush();
            objectOutputStreamToMoscow.writeObject("exit");
            objectOutputStreamToMoscow.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double roundTo2Decimals(double val) {
        DecimalFormat decimalFormat = new DecimalFormat("###.##");
        return Double.valueOf(decimalFormat.format(val));
    }

}
