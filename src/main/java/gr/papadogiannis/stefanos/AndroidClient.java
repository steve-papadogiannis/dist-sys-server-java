package gr.papadogiannis.stefanos;

import com.google.maps.model.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public final class AndroidClient {

    private MasterImpl master;
    private final int port;
    private ServerSocket serverSocket;
    private boolean isNotFinished2 = true;

    private AndroidClient(MasterImpl master, int port) {
        System.out.println("gr.papadogiannis.stefanos.AndroidClient was created.");
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
        this.isNotFinished2 = false;
    }

    private void run() {
        System.out.println("gr.papadogiannis.stefanos.AndroidClient is waiting for tasks at port " + port + " ... ");
        try {
            serverSocket = new ServerSocket(port);
            final AndroidClient androidClient = this;
            while (isNotFinished2) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    new Thread(new A(socket, serverSocket, androidClient)).start();
                } catch (SocketException ex) {
                    System.out.println("Server socket on android client was closed.");
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
        System.out.println("Android Listener is exiting...");
    }

    private class A implements Runnable {

        private Socket socket;
        private ServerSocket serverSocket;
        private ObjectInputStream objectInputStreamFromAndroid;
        private ObjectOutputStream objectOutputStreamToAndroid;
        private boolean isNotFinished = true;
        private AndroidClient androidClient;

        A(Socket socket, ServerSocket serverSocket, AndroidClient androidClient) {
            this.serverSocket = serverSocket;
            this.socket = socket;
            this.androidClient = androidClient;
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
                        master.tearDownApplication();
                        break;
                    } else {
                        final String incoming = (String) incomingObject;
                        final String[] parts = incoming.split(" ");
                        final GeoPoint startGeoPoint = new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                        final GeoPoint endGeoPoint = new GeoPoint(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                        master.setStartLatitude(startGeoPoint.getLatitude());
                        master.setStartLongitude(startGeoPoint.getLongitude());
                        master.setEndLatitude(endGeoPoint.getLatitude());
                        master.setEndLongitude(endGeoPoint.getLongitude());
                        final DirectionsResult result = master.searchCache(startGeoPoint, endGeoPoint);
                        final List<Double> directionPoints = getDirection(result);
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
