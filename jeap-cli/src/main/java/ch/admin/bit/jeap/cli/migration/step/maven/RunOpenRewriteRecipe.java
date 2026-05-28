package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs an OpenRewrite recipe using the rewrite-maven-plugin via Maven.
 */
@Slf4j
public class RunOpenRewriteRecipe implements Step {

    // Matches OpenRewrite error annotations written into pom.xml when Maven artifact downloads fail,
    // e.g. <!--~~(software.amazon.awssdk:bom-internal:2.42.36 failed. Unable to download POM...)~~>-->
    private static final Pattern MAVEN_DOWNLOADING_EXCEPTION_MARKER =
            Pattern.compile("<!--~~\\([\\s\\S]*?\\)~~>-->");

    private final Path workingDirectory;
    private final RunMaven runMaven;
    private final String recipeName;

    private static final List<String> CRITICAL_OLD_TYPE_MARKERS = List.of(
            "org.springframework.security.web.util.matcher.AntPathRequestMatcher",
            "org.springframework.boot.web.server.ErrorPage",
            "org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory",
            "org.springframework.boot.web.servlet.error.DefaultErrorAttributes"
    );

    /**
     * @param workingDirectory          the project root directory
     * @param processExecutor           the process executor for running Maven
     * @param recipeArtifactCoordinates the artifact coordinates of the recipe (e.g., "org.openrewrite.recipe:rewrite-spring:RELEASE")
     * @param activeRecipe              the fully qualified recipe name (e.g., "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
     */
    public RunOpenRewriteRecipe(Path workingDirectory, ProcessExecutor processExecutor,
                                String recipeArtifactCoordinates, String activeRecipe) {
        this.workingDirectory = workingDirectory;
        this.recipeName = activeRecipe;
        this.runMaven = new RunMaven(workingDirectory, processExecutor,
                MavenPlugin.OPENREWRITE.goal("run"),
                "-Drewrite.recipeArtifactCoordinates=" + recipeArtifactCoordinates,
                "-Drewrite.activeRecipes=" + activeRecipe,
                "-Drewrite.exportDatatables=true",
                // Allow the recipe to run even when the project has compile errors caused by
                // removed/moved types (e.g. Spring Security 7 removed AntPathRequestMatcher).
                // The recipes use text-based import matching and don't require compilation to succeed.
                "-Dmaven.compiler.failOnError=false");
    }

    @Override
    public void execute() throws Exception {
        runOpenRewriteHandlingDownloadMarkers(runMaven);

        if (hasCriticalLegacyMarkers()) {
            log.warn("Critical pre-Spring-Boot-4 markers still present after OpenRewrite run.");
            log.info("Retrying OpenRewrite once to ensure migration recipes are applied.");

            runOpenRewriteHandlingDownloadMarkers(runMaven);

            if (hasCriticalLegacyMarkers()) {
                throw new IllegalStateException(
                        "OpenRewrite completed but critical legacy markers are still present. " +
                        "Please inspect migration logs and verify recipe artifact resolution.");
            }
        }
    }

    private void runOpenRewriteHandlingDownloadMarkers(RunMaven runner) throws Exception {
        try {
            runner.execute();
        } catch (MavenCommandException e) {
            if (!e.getOutput().contains("MavenDownloadingExceptions")) {
                throw e;
            }
            // OpenRewrite failed to download some BOMs from private/internal repositories
            // (e.g. AWS-internal artifacts not available in CodeArtifact). The recipes may still
            // have run and applied their changes; OpenRewrite embeds error annotations as XML
            // comments in pom.xml files. Strip those markers and continue.
            log.warn("OpenRewrite could not resolve some Maven artifacts from private repositories. " +
                    "The recipes ran; stripping error annotations from pom.xml files and continuing.");
            stripDownloadExceptionMarkers();
        }
    }

    private boolean hasCriticalLegacyMarkers() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(workingDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaFile -> {
                        try {
                            String content = Files.readString(javaFile);
                            for (String marker : CRITICAL_OLD_TYPE_MARKERS) {
                                if (content.contains(marker)) {
                                    hits.add(workingDirectory.relativize(javaFile) + " -> " + marker);
                                    break;
                                }
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }

        if (!hits.isEmpty()) {
            log.warn("Detected legacy markers after OpenRewrite:");
            hits.stream().limit(10).forEach(hit -> log.warn("  " + hit));
        }
        return !hits.isEmpty();
    }

    private void stripDownloadExceptionMarkers() throws IOException {
        try (Stream<Path> paths = Files.walk(workingDirectory)) {
            paths.filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .filter(Files::isRegularFile)
                    .forEach(pom -> {
                        try {
                            String content = Files.readString(pom);
                            String stripped = MAVEN_DOWNLOADING_EXCEPTION_MARKER.matcher(content).replaceAll("");
                            if (!stripped.equals(content)) {
                                Files.writeString(pom, stripped);
                                log.info("Removed OpenRewrite download-error markers from {}",
                                        workingDirectory.relativize(pom));
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException("Failed to strip OpenRewrite error markers from " + pom, ex);
                        }
                    });
        }
    }

    @Override
    public String name() {
        return "Run OpenRewrite Recipe: " + recipeName;
    }
}
