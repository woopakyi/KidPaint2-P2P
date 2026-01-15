import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class WorkerThread extends Thread {
    String username;
    Socket socket;
    KidPaintServer server;
    private static final Set<WorkerThread> allThreads = Collections.synchronizedSet(new HashSet<>());

    public WorkerThread(KidPaintServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
        allThreads.add(this);
    }

    public void setUserName(String name) {
        username = name;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            server.serve(socket, this);
        } catch (IOException e) {
            synchronized(server.clientList) {
                server.clientList.remove(socket);
            }
            synchronized(server.userNames) {
                server.userNames.remove(socket);
            }
            server.broadcastUserList(); // notify all clients about the update

            System.out.println("Client disconnected!");
            throw new RuntimeException(e);
        }
    }

    public static Set<WorkerThread> getAllThreads() {
        return allThreads;
    }
}
