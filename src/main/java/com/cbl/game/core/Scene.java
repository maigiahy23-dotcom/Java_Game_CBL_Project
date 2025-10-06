package com.cbl.game.core;

import com.cbl.game.core.input.InputManager;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class Scene extends JPanel {
    private Engine engine;
    private final InputManager input = new InputManager();
    private final List<Sprite> drawables = new ArrayList<>();

    protected Scene() {
        setFocusable(true);
        setBackground(new Color(20,22,26));
        setLayout(null);
        addKeyListener(input);
    }

    final void attach(Engine e) { this.engine = e; }
    protected final Engine engine() { return engine; }
    protected final InputManager input() { return input; }

    protected final void addSprite(Sprite s) { drawables.add(s); }
    protected final void removeSprite(Sprite s) { drawables.remove(s); }

    public abstract void onLoad();
    public abstract void onDestroy();
    public abstract void update(float dt);

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawables.forEach(s -> s.draw(g2));
        g2.dispose();
    }
}
