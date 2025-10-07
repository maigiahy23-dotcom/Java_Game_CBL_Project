package com.cbl.game.net;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.Point;

/**
 * Host-authoritative server:
 * - Spawns enemies periodically, moves them toward nearest player.
 * - Broadcasts enemy spawn/pos/hp/death to all clients.
 * - Accepts POS/SHOT from clients and HIT (enemy damage).
 */
public final class NetServer {
    private final int port;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ScheduledExecutorService tick = Executors.newSingleThreadScheduledExecutor();

    private final Map<Integer, Client> clients = new ConcurrentHashMap<>();
    private final Map<Integer, Point> playerPos = new ConcurrentHashMap<>();
    private final Map<Integer, Enemy> enemies = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private int nextClientId = 1;
    private int nextEnemyId  = 1;

    private static final class Enemy {
        int id; float x, y; int hp = 50;
        Enemy(int id, float x, float y) { this.id = id; this.x = x; this.y = y; }
    }
    private static final class Client {
        final int id; final Socket socket; volatile PrintWriter out;
        Client(int id, Socket socket) { this.id = id; this.socket = socket; }
    }

    public NetServer(int port) { this.port = port; }

    public void startAsync() {
        if (running) return;
        running = true;
        pool.submit(this::acceptLoop);
        // game tick ~20Hz
        tick.scheduleAtFixedRate(this::serverStep, 50, 50, TimeUnit.MILLISECONDS);
        // spawn enemy mỗi 3s
        tick.scheduleAtFixedRate(this::spawnEnemy, 0, 3, TimeUnit.SECONDS);
        System.out.println("[Server] Listening on " + port);
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        tick.shutdownNow();
        pool.shutdownNow();
    }

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket(port)) {
            this.server = ss;
            while (running) {
                Socket s = ss.accept();
                int id = nextClientId++;
                Client c = new Client(id, s);
                clients.put(id, c);
                pool.submit(() -> handle(c));
            }
        } catch (IOException e) {
            if (running) System.err.println("[Server] acceptLoop error: " + e);
        }
    }

    private void handle(Client c) {
        try (c.socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(c.socket.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(c.socket.getOutputStream()), true)) {

            c.out = out;

            // Gửi id + toàn bộ enemy hiện có cho client mới
            out.println("WELCOME|" + c.id);
            for (Enemy e : enemies.values()) {
                out.println("ENSPAWN|" + e.id + "|" + (int)e.x + "|" + (int)e.y + "|" + e.hp);
            }
            broadcast("JOINED|" + c.id);

            String line;
            while ((line = in.readLine()) != null) {
                // Cập nhật vị trí người chơi
                if (line.startsWith("POS|")) {
                    String[] p = line.split("\\|");
                    int pid = Integer.parseInt(p[1]);
                    int px = Integer.parseInt(p[2]);
                    int py = Integer.parseInt(p[3]);
                    playerPos.put(pid, new Point(px, py));
                    broadcast(line); // vẫn phát cho tất cả
                } else if (line.startsWith("SHOT|")) {
                    broadcast(line);
                } else if (line.startsWith("HIT|")) {
                    // HIT|enemyId|dmg|byPlayerId
                    String[] p = line.split("\\|");
                    int eid = Integer.parseInt(p[1]);
                    int dmg = Integer.parseInt(p[2]);
                    Enemy e = enemies.get(eid);
                    if (e != null) {
                        e.hp -= dmg;
                        if (e.hp <= 0) {
                            enemies.remove(eid);
                            broadcast("ENDEAD|" + eid);
                        } else {
                            broadcast("ENHP|" + eid + "|" + e.hp);
                        }
                    }
                }
            }
        } catch (IOException ignore) {
        } finally {
            clients.remove(c.id);
            playerPos.remove(c.id);
            broadcast("LEAVE|" + c.id);
            System.out.println("[Server] Client " + c.id + " disconnected");
        }
    }

    private void serverStep() {
        // Move each enemy toward nearest player
        if (playerPos.isEmpty()) return;
        for (Enemy e : enemies.values()) {
            Point target = nearestPlayer(e.x, e.y);
            if (target == null) continue;
            float dx = target.x - e.x, dy = target.y - e.y;
            float len = (float)Math.hypot(dx, dy);
            if (len > 1e-3) {
                float speed = 100f; // px/s
                float dt = 0.05f;   // 50 ms tick
                e.x += (dx/len) * speed * dt;
                e.y += (dy/len) * speed * dt;
            }
            broadcast("ENPOS|" + e.id + "|" + (int)e.x + "|" + (int)e.y + "|" + e.hp);
        }
    }

    private Point nearestPlayer(float x, float y) {
        Point best = null;
        double bestD = Double.MAX_VALUE;
        for (Point p : playerPos.values()) {
            double d = p.distance(x, y);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private void spawnEnemy() {
        // Spawn ở rìa màn hình 960x540 (gần đúng), bạn chỉnh theo kích thước thật nếu khác
        int w = 960, h = 540;
        int side = ThreadLocalRandom.current().nextInt(4);
        int x = 0, y = 0;
        switch (side) {
            case 0: x = 0;           y = ThreadLocalRandom.current().nextInt(h); break;     // trái
            case 1: x = w - 32;      y = ThreadLocalRandom.current().nextInt(h); break;     // phải
            case 2: x = ThreadLocalRandom.current().nextInt(w); y = 0; break;               // trên
            case 3: x = ThreadLocalRandom.current().nextInt(w); y = h - 32; break;          // dưới
        }
        Enemy e = new Enemy(nextEnemyId++, x, y);
        enemies.put(e.id, e);
        broadcast("ENSPAWN|" + e.id + "|" + x + "|" + y + "|" + e.hp);
    }

    private void broadcast(String msg) {
        for (Client cl : clients.values()) {
            try { if (cl.out != null) cl.out.println(msg); } catch (Exception ignored) {}
        }
    }
}
