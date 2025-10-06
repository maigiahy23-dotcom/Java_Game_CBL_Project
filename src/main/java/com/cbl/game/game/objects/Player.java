package com.cbl.game.game.objects;

import com.cbl.game.core.Sprite;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public final class Player implements Sprite {
    private final Color color;
    private float x = 100, y = 100;
    private final int w = 28, h = 28;
    private final float speed = 160f;

    public Player(Color color) { this.color = color; }

    public void move(float dx, float dy) { x += dx; y += dy; }
    public void set(float nx, float ny) { x = nx; y = ny; }

    public float x() { return x; }
    public float y() { return y; }
    public float speed() { return speed; }

    @Override public void draw(Graphics2D g) {
        g.setColor(color);
        var body = new RoundRectangle2D.Float(x, y, w, h, 8, 8);
        g.fill(body);
    }
}
