# Upgrade from jEAP CLI 1.x to 2.x

Up to version 1.x, the `jeap` launcher ran the CLI in a Docker container (`ghcr.io/jeap-admin-ch/jeap-cli`). From
version 2.0.0 on, the CLI is a native binary that runs directly on your machine. The commands and their options stay
the same, only the way the CLI is installed and run changes.

The 1.x launcher does not update itself and cannot run 2.x. To upgrade, install the new launcher once as described
below. The container image is no longer published, so a 1.x installation stays on the last 1.x version.

## What Changes for You

| Topic                    | 1.x                                                       | 2.x                                                                       |
|--------------------------|-----------------------------------------------------------|---------------------------------------------------------------------------|
| How the CLI runs         | Docker container started by the launcher                  | Native binary in `~/.jeap/bin`, started by the launcher                   |
| Docker                   | Required                                                  | Not required (only if your project's build uses Testcontainers)           |
| Platforms                | Linux and macOS with Docker                               | Linux x86_64 and macOS arm64 (Apple Silicon); on Windows, use WSL         |
| Updates                  | Daily `docker pull`                                       | Daily check for a new GitHub release, download with checksum verification |
| Java, Maven and git      | Bundled in the image (JDK 25)                             | Taken from your machine: your JDK, Maven or the Maven Wrapper, and git    |
| Maven and git settings   | Mounted into the container                                | Used directly (`~/.m2/settings.xml`, `MAVEN_OPTS`, `~/.gitconfig`, ...)   |
| Services on `localhost`  | Not reachable, needed `172.17.0.1`/`host.docker.internal` | Reachable with `localhost`                                                |
| Pin a version            | `JEAP_CLI_IMAGE=ghcr.io/jeap-admin-ch/jeap-cli:<version>` | `JEAP_CLI_VERSION=<version>`                                              |
| Certificates (PCS/PAS)   | Embedded truststore of the image                          | Java truststore of your machine, see [Certificates](#certificates)        |

## Before You Start

Check that your machine meets the requirements:

```bash
uname -sm                         # Linux x86_64 or Darwin arm64
command -v curl sha256sum shasum  # curl and one of sha256sum/shasum
java -version                     # for migrations: JDK matching the target Java version (e.g. 25 for migrate java-25)
git --version
```

Maven is only needed if a project has no Maven Wrapper (`./mvnw`).

If your machine is an Intel Mac or a Linux arm64 machine, there is no 2.x binary for it. Keep using 1.x for now, see
[Staying on 1.x](#staying-on-1x).

## Upgrade

1. Find out where the 1.x launcher is installed:

   ```bash
   which -a jeap
   ```

   It is usually `/usr/local/bin/jeap` (global installation) or `~/.local/bin/jeap` (user installation).

2. Run the installer and choose the **same** location as before, so the new launcher replaces the old one:

   ```bash
   curl -sSL https://raw.githubusercontent.com/jeap-admin-ch/jeap-cli/main/install.sh | bash
   ```

   The installer downloads the 2.x binary right away and runs `jeap help`.

3. Check the installation:

   ```bash
   which -a jeap                      # only one jeap, at the location chosen above
   jeap version                       # Version: 2.x.y
   JEAP_CLI_VERBOSE=1 jeap version    # shows the binary, its version and the truststore in use
   ```

   If `which -a jeap` lists two launchers, delete the old one, e.g. `rm ~/.local/bin/jeap` or
   `sudo rm /usr/local/bin/jeap`.

## Clean Up

These steps are optional. Remove the environment variables that only the 1.x launcher used from your shell
configuration (e.g. `~/.bashrc`, `~/.zshrc`). The 2.x launcher ignores them.

- `JEAP_CLI_IMAGE` (to pin a version, use `JEAP_CLI_VERSION` instead)
- `JEAP_CLI_NO_HOST_CERTS`
- `JEAP_GH_CONFIG_DIR`, `JEAP_M2_DIR`, `JEAP_GIT_CONFIG_DIR`

Remove the 1.x container images to free disk space:

```bash
docker images ghcr.io/jeap-admin-ch/jeap-cli
docker rmi $(docker images -q ghcr.io/jeap-admin-ch/jeap-cli)
```

## Certificates

The binary makes HTTPS calls of its own for the `pcs` and `pas-backfill` commands. It does not read the operating
system trust store or the macOS Keychain. Instead, the launcher passes it the Java truststore that Maven uses as well
(see [Corporate Proxy and Certificates](installation.md#corporate-proxy-and-certificates)). If you work behind a
TLS-intercepting corporate proxy, the corporate CA must be in that truststore. If Maven already works behind the proxy,
this is usually the case.

Check which truststore is used:

```bash
JEAP_CLI_VERBOSE=1 jeap version 2>&1 | grep Truststore
```

If the corporate CA is missing, import it into the truststore shown there (`changeit` is the default password; a
system-wide truststore needs `sudo`):

```bash
keytool -importcert -keystore <truststore shown above> -alias corporate-ca -file corporate-ca.pem -storepass changeit
```

Alternatively, point the launcher to a truststore that contains the CA:

```bash
export JEAP_CLI_TRUSTSTORE=/path/to/truststore
```

## Troubleshooting

| Message                                                                     | Cause and solution                                                                                                                                                         |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `docker is not installed or not on PATH` or Docker pulls when running `jeap` | The 1.x launcher is still used. Check `which -a jeap` and replace or delete the old launcher.                                                                            |
| `Unsupported platform: ...`                                                 | There is no binary for your platform. See [Staying on 1.x](#staying-on-1x).                                                                                                |
| `Release 1.x.y has no jEAP CLI binary for ...`                               | The latest release is still a 1.x release, e.g. shortly after a new launcher was published. Try again later or pin a 2.x version with `JEAP_CLI_VERSION`.                 |
| `Could not determine the latest jEAP CLI release ...`                        | GitHub is not reachable. Check your network and the `HTTPS_PROXY`/`https_proxy` variables; `curl` uses them.                                                              |
| `Checksum verification of the jEAP CLI ... download failed.`                 | The download was incomplete or altered. The installed binary is kept. Run the command again; if it keeps failing, check your proxy.                                        |
| `PKIX path building failed` or other certificate errors in `pcs`/`pas-backfill` | The corporate CA is missing in the truststore. See [Certificates](#certificates).                                                                                       |
| Migration fails in the final `install` build                                | The project is now built with your local JDK and Maven. Check that `java -version` matches the project's Java version and that your Maven settings work for the project. |

## Staying on 1.x

The last 1.x image stays available, but it gets no updates anymore. To keep using 1.x, for example on an Intel Mac,
keep your current launcher. If you need to install the 1.x launcher again, download it from the `1.10.1` tag:

```bash
curl -fsSL https://raw.githubusercontent.com/jeap-admin-ch/jeap-cli/1.10.1/jeap -o ~/.local/bin/jeap
chmod +x ~/.local/bin/jeap
```
