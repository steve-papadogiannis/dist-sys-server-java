package gr.papadogiannis.stefanos.clients;

import gr.papadogiannis.stefanos.constants.ApplicationConstants;
import gr.papadogiannis.stefanos.masters.impl.MasterImpl;
import gr.papadogiannis.stefanos.models.GeoPoint;
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
public final class Client {

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private static final String ANDROID_CLIENT_IS_WAITING_MESSAGE = "Client is waiting for tasks at port %d...";
    private static final String SERVER_SOCKET_WAS_CLOSED_ERROR_MESSAGE = "Server socket on client was closed.";
    private static final String ANDROID_CLIENT_IS_EXITING_MESSAGE = "Client is exiting...";
    private static final String ANDROID_CLIENT_CREATION_MESSAGE = "Client was created.";

    private boolean isNotFinished = true;
    private ServerSocket serverSocket;
    private final MasterImpl master;
    private final int port;

    private Client(MasterImpl master, int port) {
        LOGGER.info(ANDROID_CLIENT_CREATION_MESSAGE);
        this.master = master;
        this.port = port;
    }

    public static void main(String[] args) {
        final MasterImpl master = new MasterImpl(args);
        master.initialize();
        final Client client = new Client(master, Integer.parseInt(args[0]));
        client.run();
    }

    private void falsifyIsNotFinishedFlag() {
        this.isNotFinished = false;
    }

    private void run() {
        LOGGER.info(String.format(ANDROID_CLIENT_IS_WAITING_MESSAGE, port));
        try {
            serverSocket = new ServerSocket(port);
            final Client client = this;
            while (isNotFinished) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    new Thread(new ClientRunnable(socket, serverSocket, client)).start();
                } catch (SocketException ex) {
                    LOGGER.info(SERVER_SOCKET_WAS_CLOSED_ERROR_MESSAGE);
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
        LOGGER.info(ANDROID_CLIENT_IS_EXITING_MESSAGE);
    }

    private static class ClientRunnable implements Runnable {

        private static final String SPACE_REGEX = " ";

        private ObjectInputStream objectInputStreamFromAndroid;
        private ObjectOutputStream objectOutputStreamToAndroid;
        private final Client client;
        private final ServerSocket serverSocket;
        private boolean isNotFinished = true;
        private final Socket socket;

        ClientRunnable(Socket socket, ServerSocket serverSocket, Client client) {
            this.client = client;
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

                    if (incomingObject.equals(ApplicationConstants.EXIT_SIGNAL)) {
                        isNotFinished = false;
                        objectInputStreamFromAndroid.close();
                        objectOutputStreamToAndroid.close();
                        socket.close();
                    } else if (incomingObject.equals(ApplicationConstants.TERMINATE_SIGNAL)) {
                        client.falsifyIsNotFinishedFlag();
                        isNotFinished = false;
                        objectInputStreamFromAndroid.close();
                        objectOutputStreamToAndroid.close();
                        socket.close();
                        if (serverSocket != null)
                            serverSocket.close();
                        client.getMaster().tearDownApplication();
                        break;
                    } else {
                        final String incoming = (String) incomingObject;
                        final String[] parts = incoming.split(SPACE_REGEX);
                        final GeoPoint startGeoPoint = new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                        final GeoPoint endGeoPoint = new GeoPoint(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                        client.getMaster().setStartLatitude(startGeoPoint.getLatitude());
                        client.getMaster().setStartLongitude(startGeoPoint.getLongitude());
                        client.getMaster().setEndLatitude(endGeoPoint.getLatitude());
                        client.getMaster().setEndLongitude(endGeoPoint.getLongitude());
                        final DirectionsResult result = client.getMaster().searchCache(startGeoPoint, endGeoPoint);
                        final List<Double> directionPoints = client.getDirection(result);
                        objectOutputStreamToAndroid.writeObject(directionPoints);
                        objectOutputStreamToAndroid.flush();
                    }
                }
            } catch (IOException | ClassNotFoundException exception) {
                LOGGER.severe(exception.toString());
            } finally {
                isNotFinished = false;
                try {
                    if (objectOutputStreamToAndroid != null)
                        objectOutputStreamToAndroid.close();
                    if (objectInputStreamFromAndroid != null)
                        objectInputStreamFromAndroid.close();
                    if (socket != null)
                        socket.close();
                } catch (IOException ioException) {
                    LOGGER.severe(ioException.toString());
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
