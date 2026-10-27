# Installation and Configuration

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

## Requirements

- Linux x86_64 or macOS arm64 (on Windows, use WSL)
- `curl` and `sha256sum` or `shasum`
- `sudo` if you want to install the launcher system-wide
- For migrations: a JDK matching the target Java version, Maven or the project's Maven Wrapper, and git

## Upgrading from 1.x

Up to version 1.x the CLI ran in a Docker container. From version 2.0.0 on, it runs as a native binary on the host and
the container image `ghcr.io/jeap-admin-ch/jeap-cli` is no longer published. The 1.x launcher cannot run 2.x, so
re-run the installer once to get the new launcher. See [Upgrade from jEAP CLI 1.x to 2.x](upgrade-to-2.md) for the
steps, what changes and troubleshooting.

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

## Corporate Proxy and Certificates

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
