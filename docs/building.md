# Building from Source

This page describes how to build the jEAP CLI from source.

## Requirements

- JDK 25; for native image builds a GraalVM installation. When using SDKMAN, you can install GraalVM with the
  following command:

  ```bash
  sdk install java 25-graalce
  sdk use java 25-graalce
  ```

## Non-native Build for Java Hotspot

To create a standard Java executable JAR file, run the following command. The JAR file will be generated in the
`jeap-cli/target` directory.

```bash
./mvnw clean package
```

## Native Build with GraalVM

To create a native executable using GraalVM, run the following command. This requires GraalVM to be installed and
configured in the build environment. The native executable will be generated in the `jeap-cli/target` directory.
The release version of the CLI is built as a native executable to improve startup time.

```bash
./mvnw clean package -Pnative
```

## Running a Locally Built Binary

To test a locally built native executable with the launcher, point `JEAP_CLI_BINARY` to it. The launcher then skips
the download and update check:

```bash
./mvnw clean package -Pnative
JEAP_CLI_BINARY=jeap-cli/target/jeap-cli ./jeap help
```
