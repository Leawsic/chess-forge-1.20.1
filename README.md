# Chess Forge Port

Forge 1.20.1 port of the [Chess](https://github.com/Leawsic/chess) mod.

The original Fabric project adds Gomoku, Go, and Xiangqi using shared 3x3
board multiblocks. This repository starts from the official Forge 1.20.1 MDK
and is the target for the Forge implementation.

## Development

Requirements: Java 17 and the included Gradle wrapper.

```text
gradlew.bat build
gradlew.bat runClient
gradlew.bat runServer
gradlew.bat runData
```

The Forge port is initialized with the original mod identity (`chess`,
version `13.1`, CC0-1.0) and will be migrated incrementally from the Fabric
implementation.
