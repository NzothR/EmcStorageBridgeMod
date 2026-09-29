# EMC Virtual Storage Bridge

A Minecraft Forge mod that exposes ProjectE personal EMC and Knowledge to
Applied Energistics 2 and Refined Storage as virtual storage.

## Target versions

- Minecraft 1.20.1
- Minecraft Forge 47.4.10
- Java 17

## Development with IntelliJ IDEA

1. Open this directory as a Gradle project in IntelliJ IDEA.
2. Run `gradlew genIntellijRuns` from a terminal if the Forge run
   configurations are missing.
3. Refresh the Gradle project in IDEA.

Build with `gradlew build` (Windows: `gradlew.bat build`).

See [the technical design](docs/EMC%20Virtual%20Storage%20Bridge%20Mod%20-%20Technical%20Design.md)
for the architecture, implementation phases, and acceptance criteria.
