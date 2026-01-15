import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class KidPaintServer {
    private String adminUsername;
    final static int CLEAR = 4;
    final static int NAME = 0, PIXELS = 1, TEXT = 2, STICKER = 3;
    static final int USERLIST = 5;
    final static int KICK = 6;

    private final String studioName;
    private ServerSocket serverSocket;
    LinkedHashMap<Socket, String> userNames = new LinkedHashMap<>();
    private static final int UDP_PORT = 55777;

    static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    private void acceptClients() {
        try {
            while (true) {
                Socket client = serverSocket.accept();
                synchronized (clientList) {
                    clientList.put(client, new DataOutputStream(client.getOutputStream()));
                }
                new WorkerThread(this, client).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startUdpListener() {
        try (DatagramSocket udp = new DatagramSocket(UDP_PORT)) {
            System.out.println("UDP listener BOUND AND LISTENING on port " + udp.getLocalPort());  // ← PROOF Jay is really listening
            udp.setBroadcast(true);

            byte[] buf = new byte[256];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                udp.receive(p);  // ← if we reach here = Jay REALLY heard something

                System.out.println("JAY HEARD A PACKET FROM " + p.getAddress() + ":" + p.getPort());
                System.out.println("  → Packet length: " + p.getLength());
                String msg = new String(p.getData(), 0, p.getLength()).trim();
                System.out.println("  → Message: '" + msg + "'");

                if (msg.equals("KIDPAINT_DISCOVER")) {
                    String reply = "KIDPAINT_REPLY;" + studioName + ";" + serverSocket.getLocalPort();
                    udp.send(new DatagramPacket(reply.getBytes(), reply.length(), p.getAddress(), p.getPort()));
                    System.out.println("  → REPLIED with: " + reply);
                }
            }
        } catch (java.net.BindException e) {
            // This is OK — another studio is already running on this PC
            System.out.println("Another studio is already hosting on this computer — UDP listener skipped (normal)");
        } catch (Exception e) {
            System.err.println("UDP LISTENER ERROR:");
            e.printStackTrace();
        }
    }

    void kickUser(String usernameToKick) {
        synchronized (clientList) {
            Socket socketToKick = null;
            synchronized (userNames) {
                for (Map.Entry<Socket, String> entry : userNames.entrySet()) {
                    if (entry.getValue().equals(usernameToKick)) {
                        socketToKick = entry.getKey();
                        break;
                    }
                }
            }
            if (socketToKick != null) {
                DataOutputStream out = clientList.get(socketToKick);
                if (out != null) {
                    try {
                        // Notify the kicked client to disconnect
                        out.write(KICK);
                        out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Add system chat entry indicating kicking
                String sysMsg = usernameToKick + " was kicked out of the studio.";
                history.add(new ChatEntry(TEXT, "", sysMsg, null, true)); // empty username and isSystem = true

                // Broadcast the system message to all connected clients
                try {
                    sendText(sysMsg.getBytes("UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Close and remove the kicked client connections
                try {
                    socketToKick.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                synchronized (userNames) {
                    userNames.remove(socketToKick);
                }
                synchronized (clientList) {
                    clientList.remove(socketToKick);
                }

                broadcastUserList(); // send updated user list to remaining clients
            }
        }
    }

    static class ChatEntry {
        int type;
        String username;
        String text;
        int[] stickerIds;
        boolean isSystem;

        ChatEntry(int type, String username, String text, int[] stickerIds, boolean isSystem) {
            this.type = type;
            this.username = username;
            this.text = text;
            this.stickerIds = stickerIds;
            this.isSystem = isSystem;
        }

        // preserve old constructor for text
        ChatEntry(int type, String username, String text) {
            this(type, username, text, null, false);
        }
    }

    HashMap<Socket, DataOutputStream> clientList = new HashMap<>();
    int[][] data; // local pixel canvas
    LinkedList<ChatEntry> history = new LinkedList<>();

    public KidPaintServer(int port, String studioName) throws IOException {

        this.studioName = studioName;
        this.serverSocket = new ServerSocket(port);

        // Start UDP listener (replies to discovery)
        new Thread(this::startUdpListener).start();

        // Start TCP client acceptor in background — THIS IS THE FIX!
        new Thread(this::acceptClients).start();

        System.out.println("Studio \"" + studioName + "\" started on port " + port);
    }



    void serve(Socket socket, WorkerThread thread) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        boolean sentInit = false;

        while (true) {
            int dataType = in.read();
            switch (dataType) {
                case NAME:
                    readName(in, thread);
                    if (!sentInit) {
                        sendCurrentState(out);
                        sentInit = true;
                    }
                    break;
                case PIXELS:
                    readPixels(in);
                    break;
                case TEXT:
                    readText(in, thread.getUsername());
                    break;
                case STICKER:
                    readSticker(in);
                    break;
                case CLEAR:
                    handleClear(thread.getUsername());
                    break;
                case KICK:  // NEW
                    // Read username to kick
                    int nameLen = in.readInt();
                    byte[] nameBytes = new byte[nameLen];
                    in.readFully(nameBytes);
                    String usernameToKick = new String(nameBytes, "UTF-8");

                    // Only allow kick if this thread is admin (could mark admin by thread or username)
                    if (thread.getUsername().equals(adminUsername)) {
                        System.out.println("Admin requested kicking user: " + usernameToKick);
                        kickUser(usernameToKick);
                    } else {
                        System.out.println("Non-admin user attempted kick: " + thread.getUsername());
                    }
                    break;
            }
        }
    }

    void handleClear(String username) throws IOException {
        // Reset the server canvas
        data = new int[20][20];

        // Add clear event to history
        String clearMsg = username + " cleared the board.";
        history.add(new ChatEntry(TEXT, username, username + " cleared the board.", null, true));
        // Broadcast the chat message to all clients
        sendText((username + " cleared the board.").getBytes());

        // Broadcast CLEAR to all clients (so their canvases reset too)
        sendClearToAll();
    }

    void sendClearToAll() {
        synchronized (clientList) {
            for (DataOutputStream out : clientList.values()) {
                try {
                    out.write(CLEAR);
                    out.flush();
                } catch (IOException ex) {
                    // ignore errors for dropped connections
                }
            }
        }
    }

    void readSticker(DataInputStream in) throws IOException {
        int nameLen = in.readInt();
        byte[] nameBytes = new byte[nameLen];
        in.read(nameBytes);
        String username = new String(nameBytes, "UTF-8");
        int count = in.readInt();
        int[] stickersIds = new int[count];
        for (int i = 0; i < count; ++i) stickersIds[i] = in.readInt();

        // Add correct batch to history!
        history.add(new ChatEntry(STICKER, username, null, stickersIds, false));

        sendSticker(nameBytes, stickersIds);
    }

    void sendSticker(byte[] nameBytes, int[] stickerIds) {
        synchronized(clientList) {
            for (DataOutputStream out: clientList.values()) {
                try {
                    out.write(STICKER);
                    out.writeInt(nameBytes.length);
                    out.write(nameBytes);
                    out.writeInt(stickerIds.length);
                    for (int stickerId : stickerIds)
                        out.writeInt(stickerId);
                    out.flush();
                } catch (IOException ex) { }
            }
        }
    }

    void readText(DataInputStream in, String username) throws IOException {
        int size = in.readInt();
        byte[] buffer = new byte[size];
        in.read(buffer, 0, size);
        String textBody = new String(buffer, 0, size);
        history.add(new ChatEntry(TEXT, username, textBody));
        sendText((username + ":" + textBody).getBytes());
    }

    void sendText(byte[] buffer) throws IOException {
        synchronized (clientList) {
            for (DataOutputStream out: clientList.values()) {
                try {
                    out.write(TEXT);
                    out.writeInt(buffer.length);
                    out.write(buffer, 0, buffer.length);
                    out.flush();
                } catch (IOException ex) {
                    System.out.println("This connection dropped! Ignore it.");
                }
            }
        }
    }

    void readName(DataInputStream in, WorkerThread thread) throws IOException {
        byte[] buffer = new byte[1024];
        int len = in.readInt();
        in.readFully(buffer, 0, len);
        String name = new String(buffer, 0, len, "UTF-8");
        System.out.println(name);
        thread.setUserName(name);
        synchronized(userNames) {
            userNames.put(thread.socket, name);
        }
        // Set admin username if first or if this thread corresponds to studio creator
        if (adminUsername == null) {
            adminUsername = name;
            System.out.println("Admin user set to: " + adminUsername);
        }
        broadcastUserList();
    }

    void sendCurrentState(DataOutputStream out) throws IOException {
        // Send pixel data (same as before)
        if (data == null) data = new int[20][20];
        LinkedList<Point> nonzero = new LinkedList<>();
        int color;
        for (int row = 0; row < data.length; row++) {
            for (int col = 0; col < data[0].length; col++) {
                color = data[row][col];
                if (color != 0) nonzero.add(new Point(col, row));
            }
        }
        if (!nonzero.isEmpty()) {
            for (int c : getUniqueColors(data)) {
                LinkedList<Point> colorPixels = new LinkedList<>();
                for (Point p : nonzero) {
                    if (data[p.y][p.x] == c)
                        colorPixels.add(p);
                }
                if (!colorPixels.isEmpty()) {
                    out.write(PIXELS);
                    out.writeInt(c);
                    out.writeInt(colorPixels.size());
                    for (Point p : colorPixels) {
                        out.writeInt(p.x);
                        out.writeInt(p.y);
                    }
                    out.flush();
                }
            }
        }

        for (ChatEntry entry : history) {
            if (entry.type == TEXT) {
                if (entry.isSystem) {
                    out.write(TEXT);
                    out.writeInt(entry.text.getBytes().length);
                    out.write(entry.text.getBytes());
                    out.flush();
                } else {
                    String msg = entry.username + ":" + entry.text;
                    out.write(TEXT);
                    out.writeInt(msg.getBytes().length);
                    out.write(msg.getBytes());
                    out.flush();
                }
            } else if (entry.type == STICKER) {
                byte[] nameBytes = entry.username.getBytes("UTF-8");
                out.write(STICKER);
                out.writeInt(nameBytes.length);
                out.write(nameBytes);
                out.writeInt(entry.stickerIds.length);     // batch count
                for (int stickerId : entry.stickerIds)
                    out.writeInt(stickerId);
                out.flush();
            }
        }
    }

    static java.util.HashSet<Integer> getUniqueColors(int[][] data) {
        java.util.HashSet<Integer> colors = new java.util.HashSet<>();
        for (int[] row : data)
            for (int color : row)
                if (color != 0) colors.add(color);
        return colors;
    }

    void readPixels(DataInputStream in) throws IOException {
        int color = in.readInt();
        int size = in.readInt();
        LinkedList<Point> list = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            int x = in.readInt();
            int y = in.readInt();
            list.add(new Point(x, y));
            if (data == null) data = new int[20][20];
            data[y][x] = color;

            if (i == 0) {
                System.out.printf("%d --> %d,%d\n", color, x, y); // print only for first pixel of batch
            }
        }
        sendPixels(color, list); // broadcast to others
    }

    void sendPixels(int color, LinkedList<Point> list) {
        synchronized (clientList) {
            for (DataOutputStream out : clientList.values()) {
                try {
                    out.write(PIXELS); // pixel info
                    out.writeInt(color);
                    out.writeInt(list.size());
                    for (Point p : list) {
                        out.writeInt(p.x);
                        out.writeInt(p.y);
                    }
                    out.flush();
                } catch (IOException ex) {
                    System.out.println("One connection is dropped!");
                }
            }
        }
    }

    void broadcastUserList() {
        synchronized(clientList) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(studioName).append("|");
                synchronized(userNames) {
                    for (String name : userNames.values()) {
                        sb.append(name).append(";");
                    }
                }
                String userListString = sb.toString();
                System.out.println("Broadcast user list: '" + userListString + "'");
                byte[] bytes = userListString.getBytes("UTF-8");

                for (DataOutputStream out : clientList.values()) {
                    out.write(USERLIST);
                    out.writeInt(bytes.length);
                    out.write(bytes);
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}