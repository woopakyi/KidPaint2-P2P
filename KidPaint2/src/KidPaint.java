import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class KidPaint extends Application {

    private static final int UDP_PORT = 55777;
    private static final String DISCOVER = "KIDPAINT_DISCOVER";
    private static final String REPLY    = "KIDPAINT_REPLY";

    @Override
    public void start(Stage primaryStage) throws Exception {

        // === PART 2: Ask for username ===
        GetNameDialog nameDialog = new GetNameDialog("KidPaint – Enter your name");
        String username = nameDialog.getPlayername();
        if (username == null || username.trim().isEmpty()) {
            Platform.exit();
            return;
        }

        // === PART 2 + 3: UDP Broadcast discovery ===
        List<StudioInfo> studios = discoverStudios();   // ← This does the UDP magic

        // Show the dialog with the list of studios (step 4)
        SelectStudioDialog studioDialog = new SelectStudioDialog("KidPaint – Select or Create Studio", studios);

        if (studioDialog.isCreateNew()) {  // User chose to create (step 6: run as server + client)
            // Prompt for studio name using existing GetNameDialog (or create a new dialog if you want)
            GetNameDialog studioNameDialog = new GetNameDialog("Enter Studio Name");
            String studioNameInput = studioNameDialog.getPlayername();
            if (studioNameInput == null || studioNameInput.trim().isEmpty()) {
                studioNameInput = username + "'s Studio";
            }

            final String finalStudioName = studioNameInput;
            final int finalPort = 12345 + new Random().nextInt(5000); // Random port

            System.out.println("Starting server on port " + finalPort + " as \"" + finalStudioName + "\"...");

            // Start server in background thread
            new Thread(() -> {
                try {
                    new KidPaintServer(finalPort, finalStudioName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            Thread.sleep(500); // Give server time to start

            // Connect to own server as client
            new MainWindow(primaryStage, username, "127.0.0.1", finalPort, finalStudioName, true);
        } else if (studioDialog.getSelectedStudio() != null) {  // User chose to join (step 5: run as client only)
            StudioInfo selected = studioDialog.getSelectedStudio();
            System.out.println("Joining: " + selected);
            new MainWindow(primaryStage, username, selected.ip, selected.port, selected.name, false);
        } else {
            // User closed dialog without choosing → exit
            Platform.exit();
        }
    }

    private List<StudioInfo> discoverStudios() throws IOException {
        List<StudioInfo> list = new CopyOnWriteArrayList<>();

        DatagramSocket socket = new DatagramSocket(); // random source port
        socket.setBroadcast(true);
        socket.setSoTimeout(3000); // original 60000

        byte[] buf = DISCOVER.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length,
                InetAddress.getByName("255.255.255.255"), UDP_PORT);

        socket.send(packet);
        System.out.println("UDP broadcast sent... waiting for replies");

        long deadline = System.currentTimeMillis() + 3500;
        while (System.currentTimeMillis() < deadline) {
            try {
                byte[] recvBuf = new byte[512]; // fresh buffer for each receive
                DatagramPacket replyPacket = new DatagramPacket(recvBuf, recvBuf.length);

                socket.receive(replyPacket);

                String msg = new String(replyPacket.getData(), 0, replyPacket.getLength()).trim();

                if (msg.startsWith("KIDPAINT_REPLY;")) {
                    String[] parts = msg.substring("KIDPAINT_REPLY;".length()).split(";", 2);
                    if (parts.length == 2) {
                        String name = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        StudioInfo studio = new StudioInfo(name, replyPacket.getAddress().getHostAddress(), port);
                        if (!list.contains(studio)) {  // optional, avoids duplicates
                            list.add(studio);
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("No more replies (timeout)");
                break; // no more responses, exit loop early
            }
        }
        socket.close();
        return list;
    }

    public static void main(String[] args) {
        launch(args);
    }
}