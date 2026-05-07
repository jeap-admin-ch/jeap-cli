# Migrate to Spring Boot 4 (Preliminary Release)

> **Note:** This migration support is a preliminary release. It may be subject to changes as Spring Boot 4 Migration
> for jEAP stabilizes.

Migrates a jEAP application to Spring Boot 4.

## Usage

Navigate to the root directory of your jEAP project and run:

```bash
jeap migrate spring-boot-4
```

Optional: enable automatic Maven error fixing with Copilot CLI and retry the migration loop:

```bash
jeap migrate spring-boot-4 --auto-fix-errors-via-copilot-cli --auto-fix-max-retries 3
```

If enabled, the migration retries when a Maven command fails: it sends the extracted Maven error output to
GitHub Copilot CLI, lets it apply file changes, and then resumes the migration from the failed step —
already completed steps are **not** repeated.

## What It Does

The migration performs the following steps in order:

1. **Prepare pom.xml for Spring Boot 4 parent upgrade** — prepares all `pom.xml` files before the parent version
   switch. This step:
   - Sets the jEAP parent version in the root `pom.xml` to the Spring Boot 4 alpha version
     (`jeap-spring-boot-parent`: `34.6.0-alpha-springboot4`, `jeap-internal-spring-boot-parent`:
     `7.0.7-alpha-springboot4`)
   - Adds explicit `<dependencyManagement>` entries for dependencies that are no longer managed by the new
     parent (e.g. `commons-io`, `commons-compress`, `jose4j`)
   - Renames or replaces dependencies that changed their artifact coordinates in Spring Boot 4
     (e.g. `wiremock-jre8-standalone` → `wiremock-standalone`, `spring-boot-starter-aop` →
     `spring-boot-starter-aspectj`)

2. **Update jEAP Parent** — updates the jEAP parent POM to the latest version using Maven's versions plugin

3. **Update jEAP Dependencies** — updates all jEAP dependency versions to the latest releases. Only
   dependencies with an explicitly declared version in the project are updated — dependencies managed by the
   parent POM are not affected.

4. **Run OpenRewrite Spring Boot 4 Recipe** — runs the [OpenRewrite](https://docs.openrewrite.org/)
   `UpgradeSpringBoot_4_0` recipe to automatically migrate Spring Boot application code, configuration,
   and dependencies

5. **Replace secrets location prefix in Spring properties** — replaces `aws-secretsmanager:` with
   `jeap-aws-secretsmanager:` in all Spring `application.yml` / `application.properties` files

6. **Verify migration with Maven install** — runs `mvn install` to confirm the migrated project builds and
   all tests pass

## Prerequisites

- The project must be a Maven-based jEAP application
- For `--auto-fix-errors-via-copilot-cli`, the CLI container must be able to run:
    - `gh auth status`
    - `copilot`
