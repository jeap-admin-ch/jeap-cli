package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.maven.RunMaven;
import ch.admin.bit.jeap.cli.migration.step.maven.SetJavaVersion;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static ch.admin.bit.jeap.cli.migration.Migrations.executeStep;

@Component
@Slf4j
public class Java25Migration implements Migration {

    private final ProcessExecutor processExecutor;

    public Java25Migration(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public void migrate(Path root) throws Exception {
        Path pomPath = root.resolve("pom.xml");
        // If pom does not exist, log error and exit

        // 1) Update jEAP parent using maven
        executeStep(new RunMaven(root, processExecutor,
                "versions:update-parent",
                "-Dincludes=groupId:artifactId:type:classifier:version",
                "-DgenerateBackupPoms=false"));

        // 2) Update java.version property in pom.xml to 25
        executeStep(new SetJavaVersion(pomPath, "25"));

        // 3) Update build image to Java 25 (Jenkinsfile, GitHub Actions Workflows)

        // 4) Update Dockerfile(s) to use Java 25 base image

        // 5) Update Maven JIB plugin configuration to use Java 25 base image

    }
}
