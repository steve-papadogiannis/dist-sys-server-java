import com.google.maps.model.DirectionsResult;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public final class AndroidClient {

    private final String name;
    private MasterImpl master;
    private final int port;
    private ServerSocket serverSocket;
    private boolean isNotFinished2 = true;

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

    private void falsifyIsNotFinishedFlag() {
        this.isNotFinished2 = false;
    }

    private void run() {
        System.out.println("AndroidClient " + name + " is waiting for tasks at port " + port + " ... ");
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
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
