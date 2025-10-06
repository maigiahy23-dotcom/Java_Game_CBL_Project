package com.cbl.game.config;

public final class GameConfig {
    private GameConfig() {}
    public static final int TICK_MS = 16;           // ~60 FPS
    public static final int DEFAULT_PORT = 7777;    // TCP port
    public static final float SEND_POS_HZ = 15f;    // position sync rate
}
