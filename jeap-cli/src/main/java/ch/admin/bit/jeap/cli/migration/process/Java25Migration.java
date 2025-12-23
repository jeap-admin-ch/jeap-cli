package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.dockerfile.UpdateDockerfileJavaVersion;
import ch.admin.bit.jeap.cli.migration.step.githubactions.UpdateJeapCodebuildImage;
import ch.admin.bit.jeap.cli.migration.step.jenkinsfile.UpdateJenkinsfileMavenImage;
import ch.admin.bit.jeap.cli.migration.step.maven.RunMaven;
import ch.admin.bit.jeap.cli.migration.step.maven.SetJavaVersion;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJibBaseImage;
import ch.admin.bit.jeap.cli.migration.step.mavenwrapper.UpdateMavenWrapper;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

import static ch.admin.bit.jeap.cli.migration.Migrations.executeOptionalStep;
import static ch.admin.bit.jeap.cli.migration.Migrations.executeStep;

@Component
@Slf4j
public class Java25Migration implements Migration {

    static final String JAVA_VERSION = "25";
    private static final Map<String, String> JENKINSFILE_IMAGE_TAG_MAPPING = Map.of(
            "eclipse-temurin", "25",
            "eclipse-temurin-node", "25-node-22",
            "eclipse-temurin-node-extras", "25-node-22-browsers"
    );

    private final ProcessExecutor processExecutor;

    public Java25Migration(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public void migrate(Path root) throws Exception {
        // 1) Update jEAP parent using maven
        executeStep(new RunMaven(root, processExecutor,
                "versions:2.19.1:update-parent",
                "-Dincludes=ch.admin.bit.jeap",
                "-DgenerateBackupPoms=false"));

        // 2) Update java.version property in pom.xml to 25
        executeStep(new SetJavaVersion(root, JAVA_VERSION));

        // 3) Update build image to Java 25 (Jenkinsfile, GitHub Actions Workflows)
        executeOptionalStep(new UpdateJenkinsfileMavenImage(root, JENKINSFILE_IMAGE_TAG_MAPPING));

        // 4) Update Dockerfile(s) to use Java 25 base images
        executeOptionalStep(new UpdateDockerfileJavaVersion(root, "eclipse-temurin", "25-jre-ubi9-minimal"));
        executeOptionalStep(new UpdateDockerfileJavaVersion(root, "jeap-runtime-coretto", "25.20251119043107"));

        // 5) Update Maven JIB plugin configuration to use Java 25 base image
        executeOptionalStep(new UpdateJibBaseImage(root, "amazoncorretto", JAVA_VERSION + "-al2023-headless"));

        // 6) Update jEAP codebuild images in GitHub Actions workflows
        executeOptionalStep(new UpdateJeapCodebuildImage(root, JAVA_VERSION + "-node-22"));

        // 7) Update Maven Wrapper
        executeStep(new UpdateMavenWrapper(root));
    }
}
