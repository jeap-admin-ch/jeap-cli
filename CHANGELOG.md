# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-01-30

### Added

- Automatically mount host CA certificates into the container to improve corporate proxy support
- New environment variable `JEAP_CLI_NO_HOST_CERTS=1` to disable automatic host certificate mounting
- Fix maven wrapper migration step to correctly update the .mvn/wrapper/maven-wrapper.properties file, keeping
  newlines as required by the mvnw script.

## [1.0.0] - 2026-01-29

### Changed

- Make maven wrapper download work behind proxies by providing curl/wget in the container image

## [0.3.1] - 2026-01-20

### Changed

- Update .sdkmanrc files in the Java 25 migration step

## [0.3.0] - 2025-12-30

### Changed

- Add new step UpdateMavenWrapper to Java 25 migration with the following actions:
    - update Maven Wrapper to latest version
    - add a jvm.config file with options to ignore warning messages

## [0.2.4] - 2025-12-23

### Fixed

- Migrate codebuild image in GH Actions workflow when additional parameters are defined between uses: and with:

## [0.2.3] - 2025-12-11

### Fixed

- Replace amazoncorretto:25-al2032-headless with amazoncorretto:25-al2023-headless

## [0.2.2] - 2025-11-26

### Fixed

- Align version numbers, display correct version when running the CLI

## [0.2.1] - 2025-11-26

### Changed

- Make installer and CLI launcher script compatible with OS X 

## [0.2.0] - 2025-11-21

### Added

- Java 25 migration command with the following migration steps:
    - Update jEAP parent version using Maven versions plugin
    - Set `java.version` and `maven.compiler.release` properties in pom.xml
    - Update build/runtime container images to Java 25 (eclipse-temurin and jeap-runtime-coretto)
    - Update JIB Maven plugin base images to Java 25
    - Update GitHub Actions workflow jeap-codebuild-java images to Java 25
- GitHub Actions workflow enhancements:
    - Trivy security scanning with git history support for secrets detection
    - Open source compliance action for third-party licenses and publiccode.yml validation

## [0.1.0] - 2027-11-12

### Added

- Initial revision
