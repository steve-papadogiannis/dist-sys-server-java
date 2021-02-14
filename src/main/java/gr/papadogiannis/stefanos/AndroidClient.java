package gr.papadogiannis.stefanos;

import com.google.maps.model.*;

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.util.logging.Logger;
import java.net.SocketException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * @author Stefanos Papadogiannis
 *
 * Created on 22/4/2017
 */
public final class AndroidClient {

    private static final Logger LOGGER = Logger.getLogger(AndroidClient.class.getName());

    private boolean isNotFinished = true;
    private ServerSocket serverSocket;
    private final MasterImpl master;
    private final int port;

    private AndroidClient(MasterImpl master, int port) {
        LOGGER.info("AndroidClient was created.");
        this.master = master;
        this.port = port;
    }

    public static void main(String[] args) {
        final MasterImpl master = new MasterImpl(args);
        master.initialize();
        final AndroidClient androidClient = new AndroidClient(master, Integer.parseInt(args[0]));
        androidClient.run();
    }

    private void falsifyIsNotFinishedFlag() {
        this.isNotFinished = false;
    }

    private void run() {
        LOGGER.info(String.format("AndroidClient is waiting for tasks at port %d... ", port));
        try {
            serverSocket = new ServerSocket(port);
            final AndroidClient androidClient = this;
            while (isNotFinished) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    new Thread(new A(socket, serverSocket, androidClient)).start();
                } catch (SocketException ex) {
                    LOGGER.info("Server socket on android client was closed.");
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
        LOGGER.info("AndroidClient is exiting...");
    }

    private static class A implements Runnable {

        private ObjectInputStream objectInputStreamFromAndroid;
        private ObjectOutputStream objectOutputStreamToAndroid;
        private final AndroidClient androidClient;
        private final ServerSocket serverSocket;
        private boolean isNotFinished = true;
        private final Socket socket;

        A(Socket socket, ServerSocket serverSocket, AndroidClient androidClient) {
            this.androidClient = androidClient;
            this.serverSocket = serverSocket;
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                objectInputStreamFromAndroid = new ObjectInputStream(socket.getInputStream());
                objectOutputStreamToAndroid = new ObjectOutputStream(socket.getOutputStream());
                waitForTasksThread();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void waitForTasksThread() {
            Object incomingObject;
            try {
                while (isNotFinished) {
                    incomingObject = objectInputStreamFromAndroid.readObject();

                    if (incomingObject.equals("exit")) {
                        isNotFinished = false;
                        objectInputStreamFromAndroid.close();
                        objectOutputStreamToAndroid.close();
                        socket.close();
                    } else if (incomingObject.equals("terminate")) {
                        androidClient.falsifyIsNotFinishedFlag();
                        isNotFinished = false;
                        objectInputStreamFromAndroid.close();
                        objectOutputStreamToAndroid.close();
                        socket.close();
                        if (serverSocket != null)
                            serverSocket.close();
                        androidClient.getMaster().tearDownApplication();
                        break;
                    } else {
                        final String incoming = (String) incomingObject;
                        final String[] parts = incoming.split(" ");
                        final GeoPoint startGeoPoint = new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                        final GeoPoint endGeoPoint = new GeoPoint(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                        androidClient.getMaster().setStartLatitude(startGeoPoint.getLatitude());
                        androidClient.getMaster().setStartLongitude(startGeoPoint.getLongitude());
                        androidClient.getMaster().setEndLatitude(endGeoPoint.getLatitude());
                        androidClient.getMaster().setEndLongitude(endGeoPoint.getLongitude());
                        final DirectionsResult result = androidClient.getMaster().searchCache(startGeoPoint, endGeoPoint);
                        final List<Double> directionPoints = androidClient.getDirection(result);
                        objectOutputStreamToAndroid.writeObject(directionPoints);
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
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public MasterImpl getMaster() {
        return master;
    }

    private List<Double> getDirection(DirectionsResult directionsResult) {
        final ArrayList<Double> listGeopoints = new ArrayList<>();
        for (DirectionsRoute route : directionsResult.routes) {
            for (DirectionsLeg leg : route.legs) {
                listGeopoints.add(leg.startLocation.lat);
                listGeopoints.add(leg.startLocation.lng);
                for (DirectionsStep step : leg.steps) {
                    final List<LatLng> arr = decodePoly(step.polyline.getEncodedPath());
                    for (LatLng anArr : arr) {
                        listGeopoints.add(anArr.lat);
                        listGeopoints.add(anArr.lng);
                    }
                }
                listGeopoints.add(leg.endLocation.lat);
                listGeopoints.add(leg.endLocation.lng);
            }
        }
        return listGeopoints;
    }

    private List<LatLng> decodePoly(String polyline) {
        final ArrayList<LatLng> poly = new ArrayList<>();
        int index = 0, len = polyline.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = polyline.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = polyline.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng position = new LatLng((double) lat / 1E5, (double) lng / 1E5);
            poly.add(position);
        }
        return poly;
    }

}
