package com.cbl.game.net;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class NetClient {
    private final String host; private final int port;
    private Socket socket;
    private PrintWriter out;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private volatile boolean connected = false;
    private volatile int myId = -1;
    private Consumer<String> onMsg;

    public NetClient(String host, int port) { this.host = host; this.port = port; }

    public void connect(Consumer<String> onMessage) {
        this.onMsg = onMessage;
        pool.submit(() -> {
            try {
                socket = new Socket(host, port);
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                connected = true;
                System.out.println("[Client] Connected " + host + ":" + port);
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.startsWith("WELCOME|")) {
                            myId = Integer.parseInt(line.split("\\|")[1]);
                            System.out.println("[Client] myId = " + myId);
                        }
                        if (onMsg != null) onMsg.accept(line);
                    }
                }
            } catch (IOException e) {
                System.err.println("[Client] connect error: " + e.getMessage());
            } finally { connected = false; }
        });
    }

    public boolean isConnected() { return connected; }
    public int getMyId() { return myId; }

    public void sendPos(float x, float y) {
        if (connected && out != null && myId != -1) out.println("POS|" + myId + "|" + (int)x + "|" + (int)y);
    }
    public void sendShot(float x, float y, float vx, float vy) {
        if (connected && out != null && myId != -1) out.println("SHOT|" + myId + "|" + (int)x + "|" + (int)y + "|" + (int)vx + "|" + (int)vy);
    }
    public void sendHit(int enemyId, int dmg) {
        if (connected && out != null && myId != -1) out.println("HIT|" + enemyId + "|" + dmg + "|" + myId);
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
        connected = false;
    }
}
