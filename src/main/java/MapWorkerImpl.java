import com.google.maps.model.DirectionsResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import javafx.util.Pair;
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
import java.util.ArrayList;
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
    private ObjectInputStream objectInputStreamFromMoscow;

    MapWorkerImpl(String name, int port) {
        System.out.println("MapWorker " + name + " was created.");
        this.name = name;
        this.port = port;
    }

    @Override
    public void run() {
        initialize();
    }

    @Override
    public void initialize() {
        System.out.println("MapWorker " + name + " is waiting for tasks at port " + port + " ... ");
        try {
            final ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                socket = serverSocket.accept();
                objectInputStream = new ObjectInputStream(socket.getInputStream());
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                waitForTasksThread();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void waitForTasksThread() {
        MapTask mapTask = null;
        try {
            while ((mapTask = (MapTask) objectInputStream.readObject()) != null) {
                System.out.println(name + " received " + mapTask);
                final Map<Pair<GeoPoint, GeoPoint>, DirectionsResult> map =
                        map(mapTask.getStartGeopoint(), mapTask.getEndGeoPoint());
                sendToReducers(map);
                notifyMaster();
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<Pair<GeoPoint, GeoPoint>, DirectionsResult> map(Object obj1, Object obj2) {
        final Mongo mongo = new Mongo("127.0.0.1", 27017);
        final DB db = mongo.getDB("local");
        final DBCollection dbCollection = db.getCollection("directions");
        final JacksonDBCollection<DirectionsResultWrapper, String> coll = JacksonDBCollection.wrap(dbCollection,
            DirectionsResultWrapper.class, String.class);
        final DBCursor<DirectionsResultWrapper> cursor = coll.find();
        final List<DirectionsResultWrapper> list = new ArrayList<>();
        if (cursor.hasNext()) {
           list.add(cursor.next());
        }
        final long ipPortHash = calculateHash(socket.getInetAddress().toString() + socket.getPort());
        final List<DirectionsResultWrapper> filteredDirectionsResultWrappers = list.stream()
            .filter(x -> calculateHash(String.valueOf(x.getStartPoint().getLatitude()) +
            String.valueOf(x.getStartPoint().getLongitude()) + String.valueOf(x.getEndPoint().getLatitude()) +
            String.valueOf(x.getEndPoint().getLongitude())) % 4 < ipPortHash).collect(Collectors.toList());
        return null;
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
    public void sendToReducers(Map<Pair<GeoPoint, GeoPoint>, DirectionsResult> map) {
        System.out.println("Sending ack to reduce worker " + ApplicationConstants.MOSCOW + " ... ");
        try {
            if (objectOutputStreamToMoscow == null) {
                openSocket(ApplicationConstants.MOSCOW_PORT);
            }
            objectOutputStreamToMoscow.writeObject(map);
            objectOutputStreamToMoscow.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openSocket(int port) {
        try {
            final Socket serverSocket = new Socket(ApplicationConstants.LOCALHOST, port);
            objectOutputStreamToMoscow = new ObjectOutputStream(serverSocket.getOutputStream());
            objectInputStreamFromMoscow = new ObjectInputStream(serverSocket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
