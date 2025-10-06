package com.cbl.game.net;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class NetServer {
    private final int port;
    private volatile boolean running;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final Map<String, float[]> positions = new ConcurrentHashMap<>();

    public NetServer(int port) { this.port = port; }

    public void startAsync() {
        if (running) return; running = true;
        var t = new Thread(this::run, "server-main"); t.setDaemon(true); t.start();
    }

    private void run() {
        try (var server = new ServerSocket(port)) {
            System.out.println("[Server] Listening on " + port);
            while (running) {
                var socket = server.accept();
                var h = new ClientHandler(socket);
                clients.add(h);
                new Thread(h, "client-" + h.id).start();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error: " + e.getMessage());
        }
    }

    private void broadcast(NetMessage m, ClientHandler except) { broadcast(m.toLine(), except); }
    private void broadcast(String line, ClientHandler except) {
        for (var c : clients) if (c != except) c.send(line);
    }

    private final class ClientHandler implements Runnable {
        final String id = UUID.randomUUID().toString().substring(0,8);
        private final Socket socket;
        private PrintWriter out;

        ClientHandler(Socket s) { this.socket = s; }

        @Override public void run() {
            System.out.println("[Server] Client " + id + " connected: " + socket);
            try (var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 var pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
                out = pw;
                send(new NetMessage(MessageType.WELCOME, id).toLine());
                for (var e : positions.entrySet())
                    send(new NetMessage(MessageType.POS, e.getKey(), Float.toString(e.getValue()[0]), Float.toString(e.getValue()[1])).toLine());

                String line;
                while ((line = in.readLine()) != null) {
                    var msg = NetMessage.parse(line);
                    switch (msg.type) {
                        case JOIN -> broadcast(new NetMessage(MessageType.JOINED, id, (msg.args.length>0?msg.args[0]:id)), this);
                        case POS -> {
                            if (msg.args.length >= 2) {
                                float x = Float.parseFloat(msg.args[0]);
                                float y = Float.parseFloat(msg.args[1]);
                                positions.put(id, new float[]{x,y});
                                broadcast(new NetMessage(MessageType.POS, id, Float.toString(x), Float.toString(y)), this);
                            }
                        }
                        case SHOT -> {
                            if (msg.args.length >= 4)
                                broadcast(new NetMessage(MessageType.SHOT, id, msg.args[0], msg.args[1], msg.args[2], msg.args[3]), this);
                        }
                        default -> {}
                    }
                }
            } catch (IOException ignored) {
            } finally {
                clients.remove(this);
                positions.remove(id);
                broadcast(new NetMessage(MessageType.LEAVE, id), this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void send(String line) { if (out != null) out.println(line); }
    }
}
