package com.cbl.game.net;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class NetClient {
    private final String host; private final int port;
    private volatile PrintWriter out;
    private final ConcurrentLinkedQueue<NetMessage> inbox = new ConcurrentLinkedQueue<>();

    public NetClient(String host, int port) { this.host = host; this.port = port; }

    public void connectAsync() throws IOException {
        var s = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
        var reader = new Thread(() -> {
            try (var in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                String line; while ((line = in.readLine()) != null) inbox.add(NetMessage.parse(line));
            } catch (IOException ignored) {}
        }, "net-reader");
        reader.setDaemon(true); reader.start();
        send(new NetMessage(MessageType.JOIN, "Player"));
    }

    public void send(NetMessage m) { if (out != null) out.println(m.toLine()); }
    public NetMessage poll() { return inbox.poll(); }
}
