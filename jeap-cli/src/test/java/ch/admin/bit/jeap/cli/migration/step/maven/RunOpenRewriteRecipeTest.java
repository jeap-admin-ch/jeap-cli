package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunOpenRewriteRecipeTest {

    @TempDir
    Path tempDir;

    @Test
    void testExecutesOpenRewriteRecipe() throws Exception {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        new RunOpenRewriteRecipe(tempDir, fakeExecutor,
                "org.openrewrite.recipe:rewrite-spring:RELEASE",
                "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0").execute();

        assertEquals(1, fakeExecutor.getExecutionCount());
        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn",
                        "-ntp",
                        MavenPlugin.OPENREWRITE.goal("run"),
                        "-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:RELEASE",
                        "-Drewrite.activeRecipes=org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0",
                        "-Drewrite.exportDatatables=true",
                        "-Dmaven.compiler.failOnError=false"),
                executed.command());
        assertEquals(tempDir, executed.workingDirectory());
    }

    @Test
    void testThrowsOnNonDownloadMavenFailure() {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(1, "BUILD FAILURE: some other error");

        assertThrows(IOException.class,
                () -> new RunOpenRewriteRecipe(tempDir, fakeExecutor,
                        "org.openrewrite.recipe:rewrite-spring:RELEASE",
                        "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0").execute());
    }

    @Test
    void testContinuesAndStripsMarkersOnMavenDownloadingExceptions() throws Exception {
        // Simulate OpenRewrite failing with MavenDownloadingExceptions and writing error markers into pom.xml
        String output = """
                [ERROR] org.openrewrite.maven.MavenDownloadingExceptions: null
                [ERROR]   org.openrewrite.maven.MavenDownloadingExceptions.append(MavenDownloadingExceptions.java:44)
                """;
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(1, output);

        // Write a pom.xml with OpenRewrite download-error markers in it
        Path subModule = tempDir.resolve("my-module");
        Files.createDirectories(subModule);
        String pomWithMarkers = """
                <?xml version="1.0"?>
                <project>
                  <dependencies>
                    <!--~~(software.amazon.awssdk:bom-internal:2.42.36 failed. Unable to download POM. HTTP 401)~~>--><dependency>
                      <groupId>ch.admin.bit.jeap</groupId>
                      <artifactId>jeap-messaging-infrastructure-kafka</artifactId>
                    </dependency>
                    <!--~~(com.amazon.corretto:AmazonCorrettoCryptoProvider:2.5.0 failed. HTTP 401)~~>--><!--~~(other-artifact:1.0 failed)~~>--><dependency>
                      <groupId>ch.admin.bit.jeap</groupId>
                      <artifactId>jeap-spring-boot-tls-starter</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """;
        Path pom = subModule.resolve("pom.xml");
        Files.writeString(pom, pomWithMarkers);

        // Should NOT throw despite Maven exit code 1
        new RunOpenRewriteRecipe(tempDir, fakeExecutor,
                "org.openrewrite.recipe:rewrite-spring:RELEASE",
                "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0").execute();

        // Markers must be stripped from the pom.xml
        String strippedPom = Files.readString(pom);
        assertEquals("""
                <?xml version="1.0"?>
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>ch.admin.bit.jeap</groupId>
                      <artifactId>jeap-messaging-infrastructure-kafka</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>ch.admin.bit.jeap</groupId>
                      <artifactId>jeap-spring-boot-tls-starter</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """, strippedPom);
    }

    @Test
    void testStepName() {
        RunOpenRewriteRecipe step = new RunOpenRewriteRecipe(tempDir, new FakeProcessExecutor(0),
                "org.openrewrite.recipe:rewrite-spring:RELEASE",
                "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0");
        assertEquals("Run OpenRewrite Recipe: org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0", step.name());
    }

    @Test
    void testWithDifferentRecipe() throws Exception {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        new RunOpenRewriteRecipe(tempDir, fakeExecutor,
                "org.openrewrite.recipe:rewrite-java:RELEASE",
                "org.openrewrite.java.migrate.UpgradeToJava21").execute();

        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn",
                        "-ntp",
                        MavenPlugin.OPENREWRITE.goal("run"),
                        "-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-java:RELEASE",
                        "-Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava21",
                        "-Drewrite.exportDatatables=true",
                        "-Dmaven.compiler.failOnError=false"),
                executed.command());
    }
}
