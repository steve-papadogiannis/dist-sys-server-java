
import com.google.maps.model.DirectionsResult;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public final class AndroidClient {

    private final String name;
    private MasterImpl master;
    private final int port;
    private ServerSocket serverSocket;
    private boolean isNotFinished = true;
    private Socket masterSocket;
    private ObjectInputStream objectInputStreamFromAndroid;
    private ObjectOutputStream objectOutputStreamToAndroid;

    private AndroidClient(MasterImpl master, String name, int port) {
        System.out.println("AndroidClient " + name + " was created.");
        this.master = master;
        this.name = name;
        this.port = port;
    }

    public static void main(String[] args) {
        final MasterImpl master = new MasterImpl();
        master.initialize();
        final AndroidClient androidClient = new AndroidClient(master, args[0], Integer.parseInt(args[1]));
        androidClient.run();
    }

    private void run() {
        System.out.println("AndroidClient " + name + " is waiting for tasks at port " + port + " ... ");
        try {
            serverSocket = new ServerSocket(port);
            while (isNotFinished) {
                masterSocket = serverSocket.accept();
                objectInputStreamFromAndroid = new ObjectInputStream(masterSocket.getInputStream());
                objectOutputStreamToAndroid = new ObjectOutputStream(masterSocket.getOutputStream());
                processQuery();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            isNotFinished = false;
            try {
                if (objectOutputStreamToAndroid != null)
                    objectOutputStreamToAndroid.close();
                if (objectInputStreamFromAndroid != null)
                    objectInputStreamFromAndroid.close();
                if (masterSocket != null)
                    masterSocket.close();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Android Listener is exiting...");
    }

    private void processQuery() {
        Object incomingObject;
        try {
            while (isNotFinished) {
                incomingObject = objectInputStreamFromAndroid.readObject();
                if (incomingObject.equals("exit")) {
                    isNotFinished = false;
                    objectInputStreamFromAndroid.close();
                    objectOutputStreamToAndroid.close();
                    masterSocket.close();
                    serverSocket.close();
                } else {
                    final String incoming = (String) incomingObject;
                    final String[] parts = incoming.split(" ");
                    final GeoPoint startGeoPoint = new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                    final GeoPoint endGeoPoint = new GeoPoint(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    final DirectionsResult result = master.searchCache(startGeoPoint, endGeoPoint);
                    objectOutputStreamToAndroid.writeObject(result);
                    objectOutputStreamToAndroid.flush();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            isNotFinished = false;
            try {
                if (objectOutputStreamToAndroid != null)
                    objectOutputStreamToAndroid.close();
                if (objectInputStreamFromAndroid != null)
                    objectInputStreamFromAndroid.close();
                if (masterSocket != null)
                    masterSocket.close();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
