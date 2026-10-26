# jEAP CLI

jeap-cli is a command-line interface tool designed to streamline the management of applications built using the
jEAP framework. It provides functionalities for version migrations, initializing new projects, and other
essential tasks to facilitate efficient application development and maintenance.

## Installation

```bash
curl -sSL https://raw.githubusercontent.com/jeap-admin-ch/jeap-cli/main/install.sh | bash
```

See [Installation and Configuration](docs/installation.md) for the requirements, the launcher configuration and
corporate proxies. Coming from 1.x? See [Upgrade from jEAP CLI 1.x to 2.x](docs/upgrade-to-2.md).

## Available Commands

| Command                                                | Description                                 |
|--------------------------------------------------------|---------------------------------------------|
| [migrate java-25](docs/migrate-java-25.md)             | Migrate a jEAP application to Java 25       |
| [migrate spring-boot-4](docs/migrate-spring-boot-4.md) | Migrate a jEAP application to Spring Boot 4 |
| [pas-backfill send](docs/pas-backfill.md)              | Submit a backfill job to the PAS            |
| [pas-backfill report](docs/pas-backfill.md)            | Read the backfill job report from the PAS   |
| [pcs maintenance](docs/pcs-maintenance.md)             | Submit PCS maintenance jobs and get reports |
| [docs preview](docs/docs-preview.md)                   | Preview the Markdown docs in the browser    |
| [docs build](docs/docs-preview.md#build-a-static-site) | Write the Markdown docs as a static site    |

## Building

See [Building from Source](docs/building.md).

## Note

This repository is part of the open source distribution of jEAP.
See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
