# Chat App (Java, Console & Swing)

Lightweight TCP chat application that includes a multi-client server, a console client, and a modern Swing GUI client in a single source file.

## Features
- Socket-based chat server with nickname registration and broadcast to all clients
- Console client for quick terminal chatting
- Swing GUI client with Discord-inspired layout, responsive panels, and status indicators
- Simple `/quit` command to leave the room gracefully
- Packaged via Gradle with Java Toolchain (Java 25)

## Prerequisites
- Java 25 (toolchain configured in Gradle)
- No external dependencies; optional local JARs can be placed in `lib/`

## Quick Start
Build (optional, Gradle wrapper will download toolchains automatically):
```bash
./gradlew build
```

### Run the server
```bash
./gradlew run --args "server 5000"
```

### Run a console client
```bash
./gradlew run --args "client localhost 5000 alice"
```

### Run a GUI client
```bash
./gradlew run --args "client-gui localhost 5000 alice"
```
(`gui` is also accepted instead of `client-gui`.)

## Usage Notes
- On first connect, the client sends the provided nickname to the server.
- Type messages and press Enter to broadcast; `/quit` disconnects.
- The GUI shows connection status and hides certain side panels on narrower windows.

## Project Structure
- `src/Main.java` — contains the entry point plus server, console client, GUI client, and styling helpers.
- `build.gradle` — application setup, toolchain (Java 25), and manifest configuration.
- `settings.gradle` — project name and Foojay toolchain resolver plugin.
- `gradlew`, `gradlew.bat`, `gradle/` — Gradle wrapper.

## License
Unspecified (no LICENSE file present). Add one if you need explicit terms.
