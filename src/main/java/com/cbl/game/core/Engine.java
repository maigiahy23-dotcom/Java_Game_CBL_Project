package com.cbl.game.core;

import com.cbl.game.config.GameConfig;
import javax.swing.*;
import java.awt.*;

public final class Engine {
    private final JFrame frame;
    private Scene currentScene;
    private final Timer loop;

    public Engine(String title, int width, int height) {
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.setVisible(true);

        loop = new Timer(GameConfig.TICK_MS, e -> {
            if (currentScene != null) {
                currentScene.update(GameConfig.TICK_MS / 1000f);
                currentScene.repaint();
            }
        });
    }

    public void run() { loop.start(); }

    public void changeScene(Scene next) {
        if (currentScene != null) {
            frame.remove(currentScene);
            currentScene.onDestroy();
        }
        currentScene = next;
        currentScene.attach(this);
        frame.add(currentScene, BorderLayout.CENTER);
        frame.revalidate();
        currentScene.onLoad();
        currentScene.requestFocusInWindow();
    }

    public JFrame window() { return frame; }
}
