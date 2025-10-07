package com.cbl.game.game.scenes;

import com.cbl.game.core.Engine;
import com.cbl.game.core.Scene;
import com.cbl.game.net.NetClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class GameplayScene extends Scene {

    // ====== CONFIG ======
    private static final int   FPS               = 60;
    private static final float DT                = 1f / FPS;
    private static final float PLAYER_SPEED      = 180f;  // px/s
    private static final float PLAYER_RADIUS     = 12f;   // ~ half-size
    private static final int   PLAYER_MAX_HP     = 100;
    private static final float IFRAME_SEC        = 0.8f;  // miễn thương sau khi trúng
    private static final float KNOCKBACK         = 220f;  // lực hất lùi
    private static final float BULLET_SPEEDY     = -300f; // bay lên
    private static final int   BULLET_LIFETIMEMS = 900;
    private static final float SEND_POS_HZ       = 15f;
    private static final float ENEMY_RADIUS      = 14f;
    private static final float ENEMY_HIT_R2      = (PLAYER_RADIUS + ENEMY_RADIUS) * (PLAYER_RADIUS + ENEMY_RADIUS);

    // ====== ENGINE / NET ======
    private final Engine engine;
    private final String host;
    private final int    port;

    public GameplayScene(Engine engine, String host, int port) {
        this.engine = engine; this.host = host; this.port = port;
    }

    // ====== PLAYER STATE ======
    private float px = 120, py = 120;
    private float vx = 0,  vy = 0;               // vận tốc (để knockback trông mượt)
    private boolean up, down, left, right, shootPressed, respawnPressed, backLobbyPressed;
    private int hp = PLAYER_MAX_HP;
    private boolean dead = false;
    private float iFrame = 0f;                   // đếm miễn thương
    private float hurtFlash = 0f;                // overlay đỏ ngắn khi trúng

    // ====== LOOP & INPUT ======
    private javax.swing.Timer timer;
    private KeyEventDispatcher keyDispatcher;

    // ====== NETWORK ======
    private NetClient net;
    private final Map<Integer, Point> ghosts  = new ConcurrentHashMap<>();
    private final Map<Integer, Enemy> enemies = new ConcurrentHashMap<>();
    private float posSendAccum = 0f;

    // ====== ENEMY (server-managed) ======
    private static final class Enemy { int id; int hp; float x,y; }

    // ====== OFFLINE FALLBACK ======
    private static final class LocalEnemy { float x,y; int hp = 50; }
    private final List<LocalEnemy> offlineEnemies = new ArrayList<>();
    private float offlineSpawnTimer = 0f;

    // ====== BULLETS ======
    private static final class Bullet { float x,y,vx,vy; int life = BULLET_LIFETIMEMS; }
    private final List<Bullet> bullets = Collections.synchronizedList(new ArrayList<>());

    @Override public void onLoad() {
        setFocusable(true);
        setDoubleBuffered(true);
        requestFocusInWindow();

        attachGlobalInput();
        startGameLoop();
        connectNet();
    }

    @Override public void onDestroy() {
        if (timer != null) timer.stop();
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
        }
        if (net != null) net.close();
    }

    @Override public void update(float dt) { /* dùng Timer */ }

    // ---------- INPUT ----------
    private void attachGlobalInput() {
        keyDispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: case KeyEvent.VK_UP:    up = true; break;
                    case KeyEvent.VK_S: case KeyEvent.VK_DOWN:  down = true; break;
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  left = true; break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: right = true; break;
                    case KeyEvent.VK_SPACE:  shootPressed   = true; break;
                    case KeyEvent.VK_R:      respawnPressed = true; break;
                    case KeyEvent.VK_ESCAPE: backLobbyPressed = true; break;
                }
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: case KeyEvent.VK_UP:    up = false; break;
                    case KeyEvent.VK_S: case KeyEvent.VK_DOWN:  down = false; break;
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  left = false; break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: right = false; break;
                    case KeyEvent.VK_SPACE:  shootPressed   = false; break;
                    case KeyEvent.VK_R:      respawnPressed = false; break;
                    case KeyEvent.VK_ESCAPE: backLobbyPressed = false; break;
                }
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    // ---------- LOOP ----------
    private void startGameLoop() {
        timer = new javax.swing.Timer(Math.round(1000f / FPS), ev -> {
            step(DT);
            repaint();
        });
        timer.start();
    }

    private void step(float dt) {
        // back to lobby?
        if (backLobbyPressed) {
            backLobbyPressed = false;
            // quay lại Lobby (cùng package -> gọi trực tiếp)
            engine.changeScene(new LobbyScene(engine));
            return;
        }

        // i-frame & flash giảm dần
        if (iFrame > 0f)     iFrame -= dt;
        if (hurtFlash > 0f)  hurtFlash -= dt;

        // chết thì chỉ chờ R để respawn
        if (dead) {
            if (respawnPressed) {
                respawnPressed = false;
                respawn();
            }
            // vẫn cho đạn bay để dọn màn hình
            updateBullets(dt, false);
            return;
        }

        // di chuyển input
        float ix = 0, iy = 0;
        if (left)  ix -= 1;
        if (right) ix += 1;
        if (up)    iy -= 1;
        if (down)  iy += 1;
        if (ix != 0 || iy != 0) {
            float len = (float)Math.hypot(ix, iy);
            ix /= len; iy /= len;
            vx = ix * PLAYER_SPEED;
            vy = iy * PLAYER_SPEED;
        } else {
            // giảm dần vận tốc khi không giữ phím (để knockback có tác dụng)
            vx *= 0.85f;
            vy *= 0.85f;
            if (Math.abs(vx) < 1) vx = 0;
            if (Math.abs(vy) < 1) vy = 0;
        }
        px += vx * dt;
        py += vy * dt;

        // bắn
        if (shootPressed) {
            shootPressed = false;
            spawnLocalBullet(px + PLAYER_RADIUS, py + PLAYER_RADIUS, 0, BULLET_SPEEDY);
            if (isOnline()) net.sendShot(px + PLAYER_RADIUS, py + PLAYER_RADIUS, 0, BULLET_SPEEDY);
        }

        // cập nhật đạn + va chạm với enemy
        updateBullets(dt, true);

        // player bị enemy tấn công (online & offline)
        handlePlayerEnemyDamage(dt);

        // gửi vị trí ~15Hz
        if (isOnline()) {
            posSendAccum += dt;
            if (posSendAccum >= (1f / SEND_POS_HZ)) {
                net.sendPos(px, py);
                posSendAccum = 0f;
            }
        } else {
            offlineFallback(dt);
        }

        // giữ trong màn hình
        int w = Math.max(getWidth(), 1), h = Math.max(getHeight(), 1);
        px = clamp(px, 16, w - 32);
        py = clamp(py, 16, h - 32);
    }

    // ---------- NETWORK ----------
    private void connectNet() {
        net = new NetClient(host, port);
        net.connect(line -> {
            try {
                String[] p = line.split("\\|");
                switch (p[0]) {
                    case "JOINED": {
                        int id = parseInt(p[1], -1);
                        if (id != net.getMyId() && id > 0) ghosts.putIfAbsent(id, new Point(120,120));
                        break;
                    }
                    case "LEAVE": {
                        int id = parseInt(p[1], -1);
                        ghosts.remove(id);
                        break;
                    }
                    case "POS": { // POS|id|x|y
                        int id = parseInt(p[1], -1);
                        int x  = parseInt(p[2], 120);
                        int y  = parseInt(p[3], 120);
                        if (id != net.getMyId() && id > 0) ghosts.put(id, new Point(x, y));
                        break;
                    }
                    case "SHOT": { // SHOT|id|x|y|vx|vy
                        Bullet b = new Bullet();
                        b.x = parseInt(p[2], 0);
                        b.y = parseInt(p[3], 0);
                        b.vx = parseInt(p[4], 0);
                        b.vy = parseInt(p[5], (int)BULLET_SPEEDY);
                        bullets.add(b);
                        break;
                    }
                    case "ENSPAWN": { // ENSPAWN|id|x|y|hp
                        Enemy e = new Enemy();
                        e.id = parseInt(p[1], -1);
                        e.x  = parseInt(p[2], 0);
                        e.y  = parseInt(p[3], 0);
                        e.hp = parseInt(p[4], 50);
                        if (e.id > 0) enemies.put(e.id, e);
                        break;
                    }
                    case "ENPOS": { // ENPOS|id|x|y|hp
                        int id = parseInt(p[1], -1);
                        Enemy e = enemies.get(id);
                        if (e != null) {
                            e.x  = parseInt(p[2], (int)e.x);
                            e.y  = parseInt(p[3], (int)e.y);
                            e.hp = parseInt(p[4], e.hp);
                            if (e.hp <= 0) enemies.remove(id);
                        }
                        break;
                    }
                    case "ENHP": { // ENHP|id|hp
                        int id = parseInt(p[1], -1);
                        Enemy e = enemies.get(id);
                        if (e != null) {
                            e.hp = parseInt(p[2], e.hp);
                            if (e.hp <= 0) enemies.remove(id);
                        }
                        break;
                    }
                    case "ENDEAD": { // ENDEAD|id
                        int id = parseInt(p[1], -1);
                        enemies.remove(id);
                        break;
                    }
                }
            } catch (Exception ignore) {}
        });
    }

    private boolean isOnline() { return net != null && net.isConnected(); }

    // ---------- BULLETS ----------
    private void spawnLocalBullet(float x, float y, float vx, float vy) {
        Bullet b = new Bullet(); b.x = x; b.y = y; b.vx = vx; b.vy = vy; bullets.add(b);
    }

    private void updateBullets(float dt, boolean checkHitEnemies) {
        synchronized (bullets) {
            Iterator<Bullet> it = bullets.iterator();
            while (it.hasNext()) {
                Bullet b = it.next();
                b.x += b.vx * dt; b.y += b.vy * dt;
                b.life -= Math.round(dt * 1000);
                if (b.life <= 0) { it.remove(); continue; }

                if (!checkHitEnemies) continue;

                if (isOnline()) {
                    int hitId = findHitEnemyId(b.x, b.y);
                    if (hitId != -1) {
                        it.remove();
                        net.sendHit(hitId, 10); // -10 HP server-side
                    }
                } else {
                    if (hitLocalEnemy(b.x, b.y)) it.remove();
                }
            }
        }
    }

    private int findHitEnemyId(float bx, float by) {
        for (Enemy e : enemies.values()) {
            float dx = e.x - bx, dy = e.y - by;
            if (dx*dx + dy*dy <= ENEMY_HIT_R2) return e.id;
        }
        return -1;
    }

    private boolean hitLocalEnemy(float bx, float by) {
        for (Iterator<LocalEnemy> it = offlineEnemies.iterator(); it.hasNext();) {
            LocalEnemy e = it.next();
            float dx = e.x - bx, dy = e.y - by;
            if (dx*dx + dy*dy <= ENEMY_HIT_R2) {
                e.hp -= 10;
                if (e.hp <= 0) it.remove();
                return true;
            }
        }
        return false;
    }

    // ---------- PLAYER TAKES DAMAGE ----------
    private void handlePlayerEnemyDamage(float dt) {
        if (iFrame > 0f) return; // đang miễn thương

        boolean collided = false;
        float cx = px, cy = py;

        // online enemies
        for (Enemy e : enemies.values()) {
            float dx = e.x - cx, dy = e.y - cy;
            if (dx*dx + dy*dy <= ENEMY_HIT_R2) { collided = true; cx = e.x; cy = e.y; break; }
        }
        // offline enemies (fallback)
        if (!collided) {
            for (LocalEnemy e : offlineEnemies) {
                float dx = e.x - cx, dy = e.y - cy;
                if (dx*dx + dy*dy <= ENEMY_HIT_R2) { collided = true; cx = e.x; cy = e.y; break; }
            }
        }

        if (collided) {
            applyDamageAndKnockback(cx, cy, 15); // -15 HP mỗi lần chạm
        }
    }

    private void applyDamageAndKnockback(float srcX, float srcY, int dmg) {
        hp -= dmg;
        if (hp <= 0) {
            hp = 0; dead = true;
        }
        // hướng hất: từ enemy -> ra xa
        float dx = px - srcX, dy = py - srcY;
        float len = (float)Math.hypot(dx, dy);
        if (len < 1e-3f) { dx = 1; dy = 0; len = 1; }
        dx /= len; dy /= len;
        vx = dx * KNOCKBACK;
        vy = dy * KNOCKBACK;

        iFrame = IFRAME_SEC;
        hurtFlash = 0.25f;
    }

    private void respawn() {
        dead = false;
        hp = PLAYER_MAX_HP;
        px = 120; py = 120;
        vx = vy = 0;
        iFrame = 1.0f; // spawn protection
        hurtFlash = 0f;
        // không động tới enemies; chỉ reset player
    }

    // ---------- OFFLINE FALLBACK ----------
    private void offlineFallback(float dt) {
        offlineSpawnTimer += dt;
        if (offlineSpawnTimer >= 3f) {
            offlineSpawnTimer = 0f;
            LocalEnemy e = new LocalEnemy();
            int w = Math.max(getWidth(), 1), h = Math.max(getHeight(), 1);
            int side = (int)(Math.random()*4);
            if (side == 0) { e.x = 0;        e.y = (float)(Math.random()*h); }
            else if (side == 1) { e.x = w-32;   e.y = (float)(Math.random()*h); }
            else if (side == 2) { e.x = (float)(Math.random()*w); e.y = 0; }
            else { e.x = (float)(Math.random()*w); e.y = h-32; }
            offlineEnemies.add(e);
        }
        // move towards player
        for (LocalEnemy e : offlineEnemies) {
            float dx = px - e.x, dy = py - e.y;
            float len = (float)Math.hypot(dx, dy);
            if (len > 1e-3) {
                float sp = 100f;
                e.x += (dx/len) * sp * DT;
                e.y += (dy/len) * sp * DT;
            }
        }
    }

    // ---------- RENDER ----------
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D gg = (Graphics2D) g.create();

        // background grid
        gg.setColor(new Color(24,24,24)); gg.fillRect(0,0,getWidth(),getHeight());
        gg.setColor(new Color(50,50,50));
        for (int i = 0; i < getWidth(); i += 32) gg.drawLine(i, 0, i, getHeight());
        for (int j = 0; j < getHeight(); j += 32) gg.drawLine(0, j, getWidth(), j);

        // local player
        int sz = 24, ix = Math.round(px), iy = Math.round(py);
        gg.setColor(new Color(90,200,255)); gg.fillRoundRect(ix, iy, sz, sz, 8, 8);
        gg.setStroke(new BasicStroke(3f)); gg.setColor(new Color(255,140,70));
        gg.drawRoundRect(ix, iy, sz, sz, 8, 8);

        // ghosts
        gg.setColor(new Color(255,210,120));
        for (Point p : ghosts.values()) gg.drawRoundRect(p.x, p.y, sz, sz, 8, 8);

        // enemies (online) – đỏ
        for (Enemy e : enemies.values()) {
            int ex = (int)e.x, ey = (int)e.y;
            gg.setColor(new Color(230,80,80));
            gg.fillOval(ex-14, ey-14, 28, 28);
            gg.setColor(Color.WHITE);
            gg.setFont(gg.getFont().deriveFont(11f));
            gg.drawString(String.valueOf(e.hp), ex-6, ey-18);
        }

        // enemies (offline) – hồng
        gg.setColor(new Color(255,140,180));
        for (LocalEnemy e : offlineEnemies) gg.fillOval((int)e.x-12, (int)e.y-12, 24, 24);

        // bullets
        gg.setColor(new Color(255,255,160));
        synchronized (bullets) { for (Bullet b : bullets) gg.fillOval((int)b.x-3, (int)b.y-3, 6, 6); }

        // HP bar (trên trái)
        drawHpBar(gg);

        // damage flash overlay
        if (hurtFlash > 0f) {
            int alpha = (int)(Math.min(hurtFlash / 0.25f, 1f) * 100);
            gg.setColor(new Color(255, 50, 50, alpha));
            gg.fillRect(0,0,getWidth(),getHeight());
        }

        // dead overlay
        if (dead) {
            gg.setColor(new Color(0,0,0,160));
            gg.fillRect(0,0,getWidth(),getHeight());
            gg.setColor(Color.WHITE);
            gg.setFont(gg.getFont().deriveFont(Font.BOLD, 28f));
            String s1 = "YOU DIED";
            String s2 = "Press R to respawn  •  Esc to return to Lobby";
            FontMetrics fm = gg.getFontMetrics();
            gg.drawString(s1, (getWidth()-fm.stringWidth(s1))/2, getHeight()/2 - 10);
            gg.setFont(gg.getFont().deriveFont(Font.PLAIN, 14f));
            fm = gg.getFontMetrics();
            gg.drawString(s2, (getWidth()-fm.stringWidth(s2))/2, getHeight()/2 + 18);
        }

        // HUD
        gg.setColor(Color.WHITE);
        gg.setFont(gg.getFont().deriveFont(12f));
        String netStr = isOnline()
                ? "NET connected  id=" + net.getMyId() + "  peers=" + ghosts.size() + "  enemies=" + enemies.size()
                : "OFFLINE (fallback enemies active)";
        gg.drawString("Move: WASD/Arrows   Shoot: SPACE   |   " + netStr, 10, 18);

        gg.dispose();
    }

    private void drawHpBar(Graphics2D gg) {
        int x = 10, y = 28, w = 160, h = 12;
        gg.setColor(new Color(60,60,60));
        gg.fillRoundRect(x, y, w, h, 8, 8);
        int curr = Math.max(0, Math.min(w, (int)(w * (hp / (float)PLAYER_MAX_HP))));
        gg.setColor(new Color(80,200,120));
        gg.fillRoundRect(x, y, curr, h, 8, 8);
        gg.setColor(Color.WHITE);
        gg.setFont(gg.getFont().deriveFont(11f));
        gg.drawString("HP: " + hp + "/" + PLAYER_MAX_HP, x + 6, y + h - 2);
    }

    // ---------- UTILS ----------
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int parseInt(String s, int defVal) { try { return Integer.parseInt(s); } catch (Exception e) { return defVal; } }
}
