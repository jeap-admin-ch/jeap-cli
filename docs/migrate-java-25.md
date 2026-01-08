# Migrate to Java 25

Migrates a jEAP application from an earlier Java version to Java 25.

## Usage

Navigate to the root directory of your jEAP project and run:

```bash
jeap migrate java-25
```

## What It Does

The migration performs the following steps:

### Required Steps

These steps must succeed for the migration to complete:

1. **Update jEAP Parent** - Updates the jEAP parent POM to the latest version using Maven's versions plugin
2. **Set Java Version** - Updates `java.version` and `maven.compiler.release` properties in `pom.xml` to 25
3. **Update Maven Wrapper** - Updates the Maven Wrapper to the latest version

### Optional Steps

These steps are executed if applicable to your project. Failures are logged as warnings but do not abort the migration:

4. **Update Jenkinsfile Maven Image** - Updates Maven build images in Jenkinsfile:
    - `eclipse-temurin` → tag `25`
    - `eclipse-temurin-node` → tag `25-node-22`
    - `eclipse-temurin-node-extras` → tag `25-node-22-browsers`

5. **Update Dockerfile Java Version** - Updates base images in Dockerfiles:
    - `eclipse-temurin` → tag `25-jre-ubi9-minimal`
    - `jeap-runtime-coretto` → tag `25.20251119043107`

6. **Update JIB Base Image** - Updates the Maven JIB plugin configuration to use `amazoncorretto:25-al2023-headless`

7. **Update GitHub Actions Codebuild Image** - Updates jEAP codebuild images in GitHub Actions workflows to use tag
   `25-node-22`

## Prerequisites

- The project must be a Maven-based jEAP application
