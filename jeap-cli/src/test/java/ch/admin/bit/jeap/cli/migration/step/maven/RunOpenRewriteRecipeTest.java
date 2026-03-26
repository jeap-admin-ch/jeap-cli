package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
                        "-U",
                        MavenPlugin.OPENREWRITE.goal("run"),
                        "-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:RELEASE",
                        "-Drewrite.activeRecipes=org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0",
                        "-Drewrite.exportDatatables=true"),
                executed.command());
        assertEquals(tempDir, executed.workingDirectory());
    }

    @Test
    void testThrowsOnMavenFailure() {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(1);

        assertThrows(IOException.class,
                () -> new RunOpenRewriteRecipe(tempDir, fakeExecutor,
                        "org.openrewrite.recipe:rewrite-spring:RELEASE",
                        "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0").execute());
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
                        "-U",
                        MavenPlugin.OPENREWRITE.goal("run"),
                        "-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-java:RELEASE",
                        "-Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava21",
                        "-Drewrite.exportDatatables=true"),
                executed.command());
    }
}
