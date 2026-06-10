# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## Build Commands

```bash
# Standard build (Java JAR)
./mvnw clean package

# Native build with GraalVM (requires GraalVM)
./mvnw clean package -Pnative

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=SetJavaVersionTest

# Run a single test method
./mvnw test -Dtest=SetJavaVersionTest#testUpdateExistingJavaVersion

# Build local Docker image (requires GraalVM, builds native executable)
./local-docker-image-build.sh

# Run locally built Docker image
JEAP_CLI_IMAGE=jeap-cli:latest ./jeap help
```

## Architecture

jEAP CLI is a Spring Shell application that provides migration commands for jEAP framework applications.

### Key Components

**Entry Point**: `JeapCLI.java` - Spring Boot application with `@EnableCommand` for registering shell commands.

**Command Layer**: `commands/` package contains Spring Shell command classes annotated with `@Command`. Commands are
grouped (e.g., "Migrations" group).

**Migration System**:

- `Migration` interface - defines `migrate(Path root)` for migration processes
- `Migrations` utility class - provides `executeStep()` and `executeOptionalStep()` for running steps with logging
- `migration/process/` - contains complete migration implementations (e.g., `Java25Migration`)
- `migration/step/` - individual migration steps implementing the `Step` interface

**Step Pattern**: Steps are small, focused units of work:

- Each step implements `Step.execute()`
- Steps are organized by type: `maven/`, `dockerfile/`, `jenkinsfile/`, `githubactions/`, `mavenwrapper/`, `sdkmanrc/`
- Optional steps (wrapped in `executeOptionalStep()`) log warnings on failure instead of aborting
- Steps use regex-based file manipulation for XML/text files

**Composite Steps**: Higher-level steps can delegate to `RunMaven` internally (e.g., `UpdateJeapParent`,
`RunOpenRewriteRecipe`). This keeps migration orchestration readable while encapsulating Maven plugin invocation
details.

**Process Execution**: `ProcessExecutor` interface with `SystemProcessExecutor` implementation for running external
commands (Maven, etc.).

### Adding New Migrations

1. Create step classes in appropriate `migration/step/` subdirectory
2. Create migration class in `migration/process/` implementing `Migration`
3. Register command in `commands/MigrationCommands` with `@Command` annotation
4. Inject migration via constructor and call `migrate()` method

### Testing

Tests use JUnit 5 with `@TempDir` for file system testing. For process execution tests, use `FakeProcessExecutor` to
mock external commands.

## Versioning & Conventions

- Semantic Versioning; all changes documented in [CHANGELOG.md](./CHANGELOG.md) (Keep a Changelog format).
- Keep entries CHANGELOG.md concise and to the point, follow existing patterns
- Determine the version to bump to by using patch for bug fixes, minor for new features, and major for breaking changes
- Use ./mvnw versions:set -DnewVersion=x.y.z -DgenerateBackupPoms=false to bump the version in all POM files
- When working on a feature branch, increase the version to `x.y.z-SNAPSHOT` in the POMs.
- When bumping the version, also update the changelog, and updates version/date in `publiccode.yml`.
- When the version on a feature branch has not yet been bumped compared to master, ask the user if a major, minor or
  patch version bump should be performed, and update the version accordingly.
