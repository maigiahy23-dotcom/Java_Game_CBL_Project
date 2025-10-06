package com.cbl.game.game.objects;

import com.cbl.game.core.Sprite;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public final class PlayerGhost implements Sprite {
    private final Color outline;
    private float x, y;
    private final int w = 26, h = 26;

    public PlayerGhost(Color outline) { this.outline = outline; }
    public void set(float nx, float ny) { x = nx; y = ny; }

    @Override public void draw(Graphics2D g) {
        var old = g.getStroke();
        g.setColor(outline);
        g.setStroke(new BasicStroke(2f));
        var shape = new RoundRectangle2D.Float(x, y, w, h, 8, 8);
        g.draw(shape);
        g.setStroke(old);
    }
}
