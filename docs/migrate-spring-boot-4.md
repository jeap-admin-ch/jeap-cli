# Migrate to Spring Boot 4

Migrates a jEAP application to Spring Boot 4.

## Usage

Navigate to the root directory of your jEAP project and run:

```bash
jeap migrate spring-boot-4
```


## What It Does

The migration performs the following steps in order:

1. **Update Maven Wrapper** — updates the Maven Wrapper (`.mvn/wrapper/maven-wrapper.properties`) to the Maven
   version expected by updated maven plugins so that all subsequent Maven-based steps run with the correct Maven version
   (skipped automatically if the project does not use the Maven Wrapper)

2. **Prepare pom.xml for Spring Boot 4 parent upgrade** — prepares all `pom.xml` files before the parent version
   switch. This step:
   - Sets the jEAP parent version in the root `pom.xml` to the pinned Spring Boot 4 baseline
      (`jeap-spring-boot-parent` 40.5.0 or `jeap-internal-spring-boot-parent` 9.1.0, depending on which is used)
    - Adds explicit `<dependencyManagement>` entries for dependencies that are no longer managed by the new
      parent BOMs
    - Renames or replaces dependencies whose artifact coordinates changed in Spring Boot 4

3. **Update jEAP Parent** — updates the jEAP parent POM to the latest stable version using Maven's versions plugin.
   If version resolution fails, the migration continues with the pinned Spring Boot 4 baseline.

4. **Update jEAP Dependencies** — updates all jEAP dependency versions to the latest releases. Only
   dependencies with an explicitly declared version in the project are updated — dependencies managed by the
   parent POM are not affected.

5. **Run jEAP OpenRewrite Spring Boot 4 Recipe** to automatically migrate Spring Boot application code, configuration,
   and dependencies

6. **Replace secrets location prefix in Spring properties** — replaces `aws-secretsmanager:` with
   `jeap-aws-secretsmanager:` in all Spring `application.yml` / `application.properties` files

7. **Format migrated code** — runs the project's configured Spotless or git-code-format plugin when available

8. **Remove redundant Spring Cloud dependency management** — removes an explicit `spring-cloud-dependencies` import
   because it is managed by the jEAP Spring Boot 4 parent

## Prerequisites

- The project must be a Maven-based jEAP application
