package gr.papadogiannis.stefanos.servers;

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
 * <p>
 * Created on 22/4/2017
 */
public final class Server {

    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    private static final String ANDROID_CLIENT_IS_WAITING_MESSAGE = "Client is waiting for tasks at port %d...";
    private static final String SERVER_SOCKET_WAS_CLOSED_ERROR_MESSAGE = "Server socket on client was closed.";
    private static final String ANDROID_CLIENT_IS_EXITING_MESSAGE = "Client is exiting...";
    private static final String ANDROID_CLIENT_CREATION_MESSAGE = "Client was created.";

    private boolean isNotFinished = true;
    private ServerSocket serverSocket;
    private final MasterImpl master;
    private final int port;

    public Server(MasterImpl master, int port) {
        LOGGER.info(ANDROID_CLIENT_CREATION_MESSAGE);
        this.master = master;
        this.port = port;
    }

    public static void main(String[] args) {
        final MasterImpl master = new MasterImpl(args);
        master.initialize();
        final Server server = new Server(master, Integer.parseInt(args[0]));
        server.run();
    }

    private void falsifyIsNotFinishedFlag() {
        this.isNotFinished = false;
    }

    public void run() {
        LOGGER.info(String.format(ANDROID_CLIENT_IS_WAITING_MESSAGE, port));
        try {
            serverSocket = new ServerSocket(port);
            final Server server = this;
            while (isNotFinished) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    new Thread(new ServerRunnable(socket, serverSocket, server)).start();
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

    private static class ServerRunnable implements Runnable {

        private static final String SPACE_REGEX = " ";

        private ObjectInputStream objectInputStreamFromAndroid;
        private ObjectOutputStream objectOutputStreamToAndroid;
        private final ServerSocket serverSocket;
        private boolean isNotFinished = true;
        private final Server server;
        private final Socket socket;

        ServerRunnable(Socket socket, ServerSocket serverSocket, Server server) {
            this.serverSocket = serverSocket;
            this.server = server;
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                objectInputStreamFromAndroid = new ObjectInputStream(socket.getInputStream());
                objectOutputStreamToAndroid = new ObjectOutputStream(socket.getOutputStream());
                waitForTasksThread();
            } catch (IOException ioException) {
                LOGGER.severe(ioException.toString());
            }
        }

        void waitForTasksThread() {
            Object incomingObject;
            try {
                while (isNotFinished) {
                    incomingObject = objectInputStreamFromAndroid.readObject();

                    if (ApplicationConstants.EXIT_SIGNAL.equals(incomingObject)) {
                        isNotFinished = false;
                        objectInputStreamFromAndroid.close();
                        objectOutputStreamToAndroid.close();
                        socket.close();
                    } else if (ApplicationConstants.TERMINATE_SIGNAL.equals(incomingObject)) {
                        server.falsifyIsNotFinishedFlag();
                        isNotFinished = false;
                        objectInputStreamFromAndroid.close();
                        objectOutputStreamToAndroid.close();
                        socket.close();
                        if (serverSocket != null)
                            serverSocket.close();
                        server.getMaster().tearDownApplication();
                        break;
                    } else {
                        final String incoming = (String) incomingObject;
                        final String[] parts = incoming.split(SPACE_REGEX);
                        final GeoPoint startGeoPoint = new GeoPoint(Double.parseDouble(parts[0]),
                                Double.parseDouble(parts[1]));
                        final GeoPoint endGeoPoint = new GeoPoint(Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]));
                        server.getMaster().setStartLatitude(startGeoPoint.getLatitude());
                        server.getMaster().setStartLongitude(startGeoPoint.getLongitude());
                        server.getMaster().setEndLatitude(endGeoPoint.getLatitude());
                        server.getMaster().setEndLongitude(endGeoPoint.getLongitude());
                        final DirectionsResult result = server.getMaster().searchCache(startGeoPoint, endGeoPoint);
                        final List<Double> directionPoints = server.getDirection(result);
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
