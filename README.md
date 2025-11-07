## jEAP CLI

jeap-cli is a command-line interface tool designed to streamline the management of applications built using the
jEAP framework. It provides functionalities for version migrations, initializing new projects, and other
essential tasks to facilitate efficient application development and maintenance.

## Building

To build the project

### Non-native build for Java Hotspot

To create a standard Java executable JAR file, run the following command. The JAR file will be generated in the
`jeap-cli/target` directory.

```bash
./mvnw clean package
```

### Native build with GraalVM

To create a native executable using GraalVM, run the following command. This requires GraalVM to be installed and 
configured in the build environment. The native executable will be generated in the `jeap-cli/target` directory.
Native executables have the advantage of faster startup times and lower memory consumption, and do not require a 
JVM to be installed on the target system or in the runtime container image.

```bash
./mvnw clean package -Pnative
```

## Note

This repository is part of the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
