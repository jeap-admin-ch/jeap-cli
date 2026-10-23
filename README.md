# jEAP CLI

jeap-cli is a command-line interface tool designed to streamline the management of applications built using the
jEAP framework. It provides functionalities for version migrations, initializing new projects, and other
essential tasks to facilitate efficient application development and maintenance.

## Installation

You can install jEAP CLI directly from the command line using `curl`. The installer asks whether to install the
`jeap` launcher system-wide under `/usr/local/bin` or for the current user under `~/.local/bin`.

```bash
curl -sSL https://raw.githubusercontent.com/jeap-admin-ch/jeap-cli/main/install.sh | bash
```

The launcher downloads the native jEAP CLI binary for your platform from the
[GitHub releases](https://github.com/jeap-admin-ch/jeap-cli/releases) to `~/.jeap/bin`, checks once a day for a new
release and verifies the checksum of every download. If the check fails, for example when offline, the installed
binary is used. To get a new version of the launcher itself, re-run the installer.

Check the installation with:

```bash
jeap version
```

### Requirements

- Linux x86_64 or macOS arm64 (on Windows, use WSL)
- `curl` and `sha256sum` or `shasum`
- `sudo` if you want to install the launcher system-wide
- For migrations: a JDK matching the target Java version, Maven or the project's Maven Wrapper, and git

### Upgrading from 1.x

Up to version 1.x the CLI ran in a Docker container. From version 2.0.0 on, it runs as a native binary on the host and
the container image `ghcr.io/jeap-admin-ch/jeap-cli` is no longer published. The 1.x launcher cannot run 2.x, so
re-run the installer once to get the new launcher. See [Upgrade from jEAP CLI 1.x to 2.x](docs/upgrade-to-2.md) for
the steps, what changes and troubleshooting.

## Configuration

The launcher can be configured using environment variables:

| Variable              | Purpose                                                                     |
|-----------------------|-----------------------------------------------------------------------------|
| `JEAP_CLI_BINARY`     | Run this binary instead of the managed one; skips download and update check |
| `JEAP_CLI_VERSION`    | Pin a release version instead of the latest one                             |
| `JEAP_CLI_VERBOSE`    | Print the resolved binary, truststore and final command to stderr           |
| `JEAP_CLI_TRUSTSTORE` | Truststore file passed to the binary; takes precedence over auto-detection  |

Maven, git and npm run as separate processes with your usual configuration (`~/.m2/settings.xml`, `MAVEN_OPTS`,
`~/.gitconfig`, proxy environment variables, etc.).

### Corporate Proxy and Certificates

A native binary does not read the operating system trust store. For its own HTTPS calls (e.g. the `pcs` and
`pas-backfill` commands), the launcher passes the first readable Java truststore of the following list to the binary:

1. `JEAP_CLI_TRUSTSTORE`, if set (the launcher fails if the file is not readable)
2. `/etc/ssl/certs/java/cacerts` (Debian/Ubuntu)
3. `/etc/pki/ca-trust/extracted/java/cacerts` (Fedora/RHEL)
4. `$JAVA_HOME/lib/security/cacerts`
5. On macOS: `$(/usr/libexec/java_home)/lib/security/cacerts`

This is the truststore Maven uses as well, so a corporate CA imported for Maven is also trusted by the CLI. If none
is found, the binary uses its embedded default truststore. On macOS, the Keychain is not used; import the corporate CA
into the JDK truststore or point `JEAP_CLI_TRUSTSTORE` to a truststore that contains it.

## Available Commands

| Command                                                | Description                                 |
|--------------------------------------------------------|---------------------------------------------|
| [migrate java-25](docs/migrate-java-25.md)             | Migrate a jEAP application to Java 25       |
| [migrate spring-boot-4](docs/migrate-spring-boot-4.md) | Migrate a jEAP application to Spring Boot 4 |
| [pas-backfill send](docs/pas-backfill.md)              | Submit a backfill job to the PAS            |
| [pas-backfill report](docs/pas-backfill.md)            | Read the backfill job report from the PAS   |
| [pcs maintenance](docs/pcs-maintenance.md)              | Submit PCS maintenance jobs and get reports |
| [docs preview](docs/docs-preview.md)                   | Preview the Markdown docs in the browser    |
| [docs build](docs/docs-preview.md#build-a-static-site) | Write the Markdown docs as a static site    |

## Building

This section describes how to build the jEAP CLI from source. Note that for native image builds a GraalVM installation
is required. When using SDKMAN, you can install GraalVM with the following command:

```bash
sdk install java 25-graalce
sdk use java 25-graalce
```

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

### Running a Locally Built Binary

To test a locally built native executable with the launcher, point `JEAP_CLI_BINARY` to it. The launcher then skips
the download and update check:

```bash
./mvnw clean package -Pnative
JEAP_CLI_BINARY=jeap-cli/target/jeap-cli ./jeap help
```

## Note

This repository is part of the open source distribution of jEAP.
See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
