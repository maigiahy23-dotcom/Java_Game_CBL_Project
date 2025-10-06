package com.cbl.game.game.scenes;

import com.cbl.game.config.GameConfig;
import com.cbl.game.core.Engine;
import com.cbl.game.core.Scene;
import com.cbl.game.net.NetServer;

import javax.swing.*;
import java.awt.*;

public final class LobbyScene extends Scene {
    private final Engine engine;
    private NetServer server;

    public LobbyScene(Engine engine) { this.engine = engine; }

    @Override public void onLoad() {
        setLayout(new GridBagLayout());
        var c = new GridBagConstraints(); c.insets = new Insets(6,6,6,6); c.fill = GridBagConstraints.HORIZONTAL;

        var title = new JLabel("Co-op Grid Shooter", SwingConstants.CENTER);
        title.setForeground(Color.WHITE); title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        c.gridx=0; c.gridy=0; c.gridwidth=2; add(title, c);

        var hostPort = new JTextField(Integer.toString(GameConfig.DEFAULT_PORT), 10);
        var joinHost = new JTextField("127.0.0.1", 12);
        var joinPort = new JTextField(Integer.toString(GameConfig.DEFAULT_PORT), 6);
        var btnHost = new JButton("Create Lobby (Host)");
        var btnJoin = new JButton("Join Lobby");

        c.gridwidth=1; c.gridy=1; c.gridx=0; add(new JLabel("Host Port:"), c); c.gridx=1; add(hostPort, c);
        c.gridy=2; c.gridx=0; add(new JLabel("Join Host:"), c); c.gridx=1; add(joinHost, c);
        c.gridy=3; c.gridx=0; add(new JLabel("Join Port:"), c); c.gridx=1; add(joinPort, c);
        c.gridy=4; c.gridx=0; add(btnHost, c); c.gridx=1; add(btnJoin, c);

        btnHost.addActionListener(e -> {
            int port = Integer.parseInt(hostPort.getText().trim());
            server = new NetServer(port); server.startAsync();
            engine.changeScene(new GameplayScene(engine, "127.0.0.1", port));
        });
        btnJoin.addActionListener(e -> {
            String host = joinHost.getText().trim();
            int port = Integer.parseInt(joinPort.getText().trim());
            engine.changeScene(new GameplayScene(engine, host, port));
        });
    }

    @Override public void onDestroy() {}
    @Override public void update(float dt) {}
}
