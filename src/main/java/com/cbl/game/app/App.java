package com.cbl.game.app;

import com.cbl.game.core.Engine;
import com.cbl.game.game.scenes.LobbyScene;

public final class App {
    private App() {}
    public static void main(String[] args) {
        var engine = new Engine("Co-op Grid Shooter", 960, 540);
        engine.changeScene(new LobbyScene(engine));
        engine.run();
    }
}
