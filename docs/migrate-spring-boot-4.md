# Migrate to Spring Boot 4 (Preliminary Release)

> **Note:** This migration support is a preliminary release. It may be subject to changes as Spring Boot 4 Migration
> for jEAP stabilizes.

Migrates a jEAP application to Spring Boot 4.

## Usage

Navigate to the root directory of your jEAP project and run:

```bash
jeap migrate spring-boot-4
```

## What It Does

The migration performs the following steps:

1. **Update jEAP Parent** - Updates the jEAP parent POM to the latest version using Maven's versions plugin
2. **Update jEAP Dependencies** - Updates all jEAP dependency versions to the latest releases. Only dependencies with an
   explicitly declared version in the project are updated - dependencies whose version is managed by the parent POM are
   not affected.
3. **Run OpenRewrite Spring Boot 4 Recipe** - Runs the [OpenRewrite](https://docs.openrewrite.org/)
   `UpgradeSpringBoot_4_0` recipe to automatically migrate Spring Boot application code, configuration, and dependencies

## Prerequisites

- The project must be a Maven-based jEAP application
