package com.cbl.game.game.scenes;

import com.cbl.game.config.GameConfig;
import com.cbl.game.core.Engine;
import com.cbl.game.core.Scene;
import com.cbl.game.core.Sprite;
import com.cbl.game.game.objects.Player;
import com.cbl.game.game.objects.PlayerGhost;
import com.cbl.game.net.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class GameplayScene extends Scene {
    private final Engine engine; private final String host; private final int port;
    private NetClient net;
    private Player me;
    private final Map<String, PlayerGhost> ghosts = new HashMap<>();
    private float sendAccum;

    public GameplayScene(Engine engine, String host, int port) { this.engine = engine; this.host = host; this.port = port; }

    @Override public void onLoad() {
        addSprite(new GridBackground());
        me = new Player(new Color(90,200,255));
        addSprite(me);

        try {
            net = new NetClient(host, port);
            net.connectAsync();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(engine.window(), "Cannot connect: " + ex.getMessage());
        }
    }

    @Override public void onDestroy() {}

    @Override public void update(float dt) {
        float vx=0, vy=0, sp = me.speed();
        if (input().isDown(KeyEvent.VK_A) || input().isDown(KeyEvent.VK_LEFT))  vx -= sp;
        if (input().isDown(KeyEvent.VK_D) || input().isDown(KeyEvent.VK_RIGHT)) vx += sp;
        if (input().isDown(KeyEvent.VK_W) || input().isDown(KeyEvent.VK_UP))    vy -= sp;
        if (input().isDown(KeyEvent.VK_S) || input().isDown(KeyEvent.VK_DOWN))  vy += sp;
        if (vx!=0 || vy!=0) {
            float len = (float)Math.sqrt(vx*vx + vy*vy); me.move(vx/len*sp*dt, vy/len*sp*dt);
        }

        sendAccum += dt;
        float period = 1f / GameConfig.SEND_POS_HZ;
        if (net != null && sendAccum >= period) {
            sendAccum = 0f;
            net.send(new NetMessage(MessageType.POS, Float.toString(me.x()), Float.toString(me.y())));
        }

        if (net != null) {
            NetMessage m; while ((m = net.poll()) != null) {
                switch (m.type) {
                    case WELCOME -> { /* myId = m.args[0] if needed */ }
                    case POS -> {
                        String id = m.args[0];
                        float x = Float.parseFloat(m.args[1]);
                        float y = Float.parseFloat(m.args[2]);
                        var ghost = ghosts.computeIfAbsent(id, k -> { var g = new PlayerGhost(new Color(255,160,90)); addSprite(g); return g; });
                        ghost.set(x, y);
                    }
                    case LEAVE -> {
                        var g = ghosts.remove(m.args[0]);
                        if (g != null) removeSprite(g);
                    }
                    default -> {}
                }
            }
        }
    }

    private static final class GridBackground implements Sprite {
        @Override public void draw(Graphics2D g) {
            var size = g.getClipBounds();
            g.setColor(new Color(35,38,45));
            for (int x=0; x<size.width; x+=32) g.drawLine(x,0,x,size.height);
            for (int y=0; y<size.height; y+=32) g.drawLine(0,y,size.height,y);
        }
    }
}
