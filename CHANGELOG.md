# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
