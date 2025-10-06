# Co-op Grid Shooter (Java + Swing + Gradle)

Two players join the same grid world and see each other move via simple TCP networking.

## Requirements
- Java 17+
- Gradle (or add wrapper later)

## Run
```bash
./gradlew run           # macOS/Linux
# or
gradlew.bat run         # Windows
```

## Packages
- `com.cbl.game.app` — entry point
- `com.cbl.game.config` — constants
- `com.cbl.game.core` — Engine/Scene/Sprite and input/math
- `com.cbl.game.net` — simple TCP client/server + messages
- `com.cbl.game.game.*` — gameplay objects & scenes
