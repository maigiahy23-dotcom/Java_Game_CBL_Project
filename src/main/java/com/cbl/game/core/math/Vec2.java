package com.cbl.game.core.math;

public final class Vec2 {
    public float x, y;
    public Vec2(float x, float y) { this.x = x; this.y = y; }
    public Vec2 copy() { return new Vec2(x, y); }
    public Vec2 add(float dx, float dy) { x += dx; y += dy; return this; }
    public static float len(float x, float y) { return (float)Math.sqrt(x*x + y*y); }
}
