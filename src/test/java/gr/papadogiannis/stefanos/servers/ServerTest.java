package gr.papadogiannis.stefanos.servers;

import gr.papadogiannis.stefanos.mappers.impl.MapWorkerImpl;
import gr.papadogiannis.stefanos.masters.impl.MasterImpl;
import gr.papadogiannis.stefanos.reducers.impl.ReduceWorkerImpl;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ServerTest {

    @Test
    @Ignore
    public void sendDirections() throws IOException, ClassNotFoundException {

        final Runnable runnable = () -> {
            final ReduceWorkerImpl reduceWorker = new ReduceWorkerImpl(5559);
            reduceWorker.run();
        };
        final Thread reduceWorkerThread = new Thread(runnable);
        reduceWorkerThread.start();

        final List<Thread> mapWorkersThreads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int finalI = i;
            Runnable mapWorkerRunnable = () -> {
                final MapWorkerImpl mapWorker = new MapWorkerImpl(
                        5555 + finalI,
                        "localhost",
                        5559);
                mapWorker.run();
            };
            Thread mapWorkerThread = new Thread(mapWorkerRunnable);
            mapWorkerThread.start();
            mapWorkersThreads.add(mapWorkerThread);
        }

        final String[] args = new String[] {
                "",
                "localhost",
                "5555",
                "localhost",
                "5556",
                "localhost",
                "5557",
                "localhost",
                "5558",
                "localhost",
                "5559"
        };
        Runnable serverRunnable = () -> {
            final MasterImpl master = new MasterImpl(args);
            master.initialize();
            final Server server = new Server(master, 8080);
            server.run();
        };
        final Thread serverThread = new Thread(serverRunnable);
        serverThread.start();

        final Socket socket = new Socket("localhost", 8080);
        final ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        objectOutputStream.writeObject("38.047972167426146 23.803995667062868 38.04941691585683 23.79992943834163");
        objectOutputStream.flush();
        final ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
        final Object o = objectInputStream.readObject();
        System.out.println(o);
    }

}
