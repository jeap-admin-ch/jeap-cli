# jEAP CLI

jeap-cli is a command-line interface tool designed to streamline the management of applications built using the
jEAP framework. It provides functionalities for version migrations, initializing new projects, and other
essential tasks to facilitate efficient application development and maintenance.

## Installation

You can install jEAP CLI directly from the command line using `curl`.  This will download and install the launcher
script system-wide under `/usr/local/bin/jeap`, making the `jeap` command available in your environment.

```bash
curl -sSL https://raw.githubusercontent.com/jeap-admin-ch/jeap-cli/main/install.sh | bash
```

The CLI will update itself regularly.

### Requirements

- A working local Docker environment
- curl installed on your system
- sudo installed if you want to install the CLI system-wide
- Linux (tested on Ubuntu, works on most distributions) or Mac OS X

## Available Commands

| Command                                    | Description                           |
|--------------------------------------------|---------------------------------------|
| [migrate java-25](docs/migrate-java-25.md) | Migrate a jEAP application to Java 25 |

## Building

To build the project

### Non-native build for Java Hotspot

To create a standard Java executable JAR file, run the following command. The JAR file will be generated in the
`jeap-cli/target` directory.

```bash
./mvnw clean package
```

### Native build with GraalVM

To create a native executable using GraalVM, run the following command. This requires GraalVM to be installed and 
configured in the build environment. The native executable will be generated in the `jeap-cli/target` directory.
The release version of the CLI is built as a native executable to improve startup time.

```bash
./mvnw clean package -Pnative
```

### Local Docker Image Build

The production CLI is deployed as a Docker image that includes the native executable and Maven for building projects.
For testing the resulting image in the local development environment, use the provided build script that handles both
the native build and Docker image creation.

Make sure your JAVA_HOME environment variable is set to a GraalVM JDK, and run the following command:

```bash
./local-docker-image-build.sh
```

The script automatically detects and handles:

- Proxy configuration from environment variables (`HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY`)
- Corporate CA certificates from the system trust store are passed to the CLI container

The resulting Docker image will be tagged as `jeap-cli:latest`, and can be run using the following command:

```bash
export JEAP_CLI_IMAGE=jeap-cli:latest && ./jeap help
```

## Note

This repository is part of the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
