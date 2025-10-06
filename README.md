# Co‑op Grid Shooter (Java + Swing)

A tiny **multiplayer co‑op** game where 2 players join the same grid world, **see each other**, and **fight enemies** that move toward them. Built with **Java + Swing** to focus on fundamentals (rendering, input, simple networking).

---

## Key Goals

* **Networking (current focus):** Two players join one session and see each other’s position & actions.
* **Build tools (learning):** Understand options (plain `javac`, Gradle/Maven), how they work, and trade‑offs.
* **Extendable enemies:** Start with a base `Enemy` class; add variants later (stronger, ranged…).

---

## Features (MVP)

* Top‑down 2D grid world (simple camera).
* Local rendering with Swing (`JPanel` + `paintComponent`).
* Two players in one lobby:

  * Both players are rendered.
  * Both players’ **input** & **movement** are synchronized.
  * Players can **shoot**; bullets show up for both clients.
* Basic enemies:

  * Spawn around the map (same for all clients).
  * Move toward players.
  * Take damage and die.

> After MVP, add polish (HUD, sounds, different enemy types).

---

## Controls

* **Move:** Arrow keys or WASD
* **Shoot:** Space
* **Pause/Back:** Esc *(planned)*

---

## Project Structure (planned/actual)

```
src/
  App.java                   // entry point
  Engine/
    Engine.java              // window & scene management
    Scene.java               // a screen/panel with update & render
    GameObject.java          // base object with position & draw()
    (Networking helpers...)  // optional: serialization, queues
  Scenes/
    LobbyScene.java          // create/join lobby UI
    CreateLobbyScene.java    // host lobby UI
    GameplayScene.java       // main game (players, enemies, bullets)
  GameObjects/
    Player.java              // local player sprite
    PlayerGhost.java         // remote player representation
    Bullet.java
    Enemy.java               // abstract class
    EnemyBasic.java          // simple chaser
  Networking/
    Server.java              // host‑authoritative (TCP for MVP)
    Client.java              // client connection + inbox queue
Assets/
  Player.png
README.md
```

---

## How It Works (High Level)

### Rendering & Scenes

* `Engine` owns the main window (`JFrame`) and the **current `Scene`**.
* Each `Scene` is a `JPanel`:

  * Holds a list of `GameObject`s (player, enemies, bullets…).
  * `update()` → game logic; `repaint()` → `paintComponent()` draws objects.

### Input

* `Scene` adds a `KeyListener` and tracks pressed keys.
* `Player` updates position/velocity based on key states.

### Networking (simple & robust for MVP)

* **Topology:** Host‑authoritative TCP server.
* **Messages:** Text lines (e.g., `JOIN|name`, `POS|id|x|y`, `SHOT|id|x|y|vx|vy`).
* **Server:**

  * Receives updates from clients.
  * Broadcasts state to all clients.
  * Keeps a `Map<playerId, position>`.
* **Client:**

  * Sends local state (position, shots) at a fixed rate.
  * Reads server messages on a background thread → pushes into a thread‑safe inbox → `Scene.update()` consumes and updates `PlayerGhost`s, bullets, etc.

> Later upgrades: UDP for smoother movement, lerp/prediction, sequence numbers.

---

## Build & Run

### Gradle (recommended as the project grows)

1. Initialize once:

```bash
gradle init --type java-application
```

2. Move sources under `app/src/main/java` or adjust Gradle `sourceSets`.
3. Run:

```bash
gradle run
```

> Java 17+ recommended.

---

## Configuration

* **Default port:** `7777` (TCP).
* **Local test:** Run `Server` on your machine, then start two clients pointing to `127.0.0.1:7777`.
* **LAN:** Client connects to host machine’s LAN IP (e.g., `192.168.1.50:7777`).
* **Internet:** Requires **port forwarding** on the host’s router (forward TCP/7777 to the host PC).

---

## Roadmap / Todo (with acceptance criteria)

### Setup

* [ ] **Setup window**
  *AC:* Opens a `JFrame` with fixed size; closes cleanly.
* [ ] **Add join/create lobby button**
  *AC:* Lobby scene shows two buttons; clickable; keyboard focus retained for input.
* [ ] **Add lobby screen and display all players**
  *AC:* Shows a list of joined players’ names/IDs; updates on join/leave.
* [ ] **Make create lobby work**
  *AC:* Clicking “Create” starts `Server` at configured port, shows lobby info (IP/port).
* [ ] **Make join lobby work**
  *AC:* Client can enter IP/port; connects; receives `WELCOME`/`YOU` messages.
* [ ] **Make the start game work**
  *AC:* Host can start; both navigate to `GameplayScene` with same seed/state.

### Game basics

* [ ] **Basic object rendering**
  *AC:* Player sprite draws at `(x,y)`; no exceptions thrown.
* [ ] **Camera**
  *AC:* World scrolls or clamps; player remains visible; no jitter.
* [ ] **Both players are rendered on screen**
  *AC:* Local `Player` + remote `PlayerGhost` visible with distinct colors/labels.
* [ ] **Both players input work and they can see each other move around**
  *AC:* Movement sync at 10–20 Hz; ≤200 ms visible delay; no desync on reconnect.
* [ ] **Players can shoot bullets and both players see the bullets**
  *AC:* Press Space spawns bullet on both clients; bullets despawn off‑screen or on hit.

### Enemies (`Enemy` is abstract; add variants later)

* [ ] **Enemies randomly spawn around for all clients**
  *AC:* Server sends spawn msgs; all clients see the same enemies with the same IDs.
* [ ] **Enemies can take damage**
  *AC:* Bullet–enemy collision reduces HP; server authoritatively resolves.
* [ ] **Enemies can die**
  *AC:* On 0 HP, server announces death; all clients remove the enemy.
* [ ] **Enemies move towards players**
  *AC:* Basic chase behavior; tick‑based position updates; smooth on clients.
* [ ] **Enemies can attack player**
  *AC:* On contact or within range, player loses HP; shared defeat/victory rules.

---

## Build Tools — What We’ll Learn

* **Plain `javac`:** fastest to start; good to grasp fundamentals.
* **Gradle:** dependency management, tasks, run/debug, packaging.
* **Maven:** similar goals; XML‑driven; widely used in enterprise.
* **Why it matters:** reproducible builds, easier CI, modularization as the project grows.

---

## Contributing (Team Workflow)

1. Create a feature branch (e.g., `feature/network-pos`, `feature/enemy-basic`).
2. Keep PRs small and focused (≤ 300 lines if possible).
3. Add simple test scene/manual steps in the PR description.
4. Code style: small classes, clear names, no unchecked exceptions in the game loop.
5. Review: one teammate approves before merge.

---

## Troubleshooting

* **Nothing moves:** Ensure the `Scene` grabs focus (`setFocusable(true)`, `requestFocusInWindow()`).
* **High CPU or UI freeze:** Use `javax.swing.Timer` (≈16 ms) or a background thread; never block the EDT.
* **`Address already in use`:** Port already bound — change port or stop the leftover process.
* **Can’t connect over LAN:** Check firewall; verify host IP with `ipconfig/ifconfig`.
* **Sprites not loading:** Use classpath resources (`getResource("/Assets/Player.png")`) instead of OS‑specific paths.

---

## License

Choose one (MIT recommended for learning projects). Add a `LICENSE` file at repo root.

---

## Credits

Built by your team as a study project on game loops, rendering, and networking with Java + Swing.
