package com.cbl.game.core.input;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public final class InputManager extends KeyAdapter {
    private final Set<Integer> down = new HashSet<>();

    @Override public void keyPressed(KeyEvent e) { down.add(e.getKeyCode()); }
    @Override public void keyReleased(KeyEvent e) { down.remove(e.getKeyCode()); }

    public boolean isDown(int keyCode) { return down.contains(keyCode); }
}
