package com.cbl.game.net;

public final class NetMessage {
    public final MessageType type;
    public final String[] args;

    public NetMessage(MessageType type, String... args) {
        this.type = type; this.args = args;
    }

    public String toLine() {
        var sb = new StringBuilder(type.name());
        for (var a : args) sb.append('|').append(a);
        return sb.toString();
    }

    public static NetMessage parse(String line) {
        var parts = line.split("\\|", -1);
        var t = MessageType.valueOf(parts[0]);
        var rest = new String[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 1, rest, 0, rest.length);
        return new NetMessage(t, rest);
    }
}
