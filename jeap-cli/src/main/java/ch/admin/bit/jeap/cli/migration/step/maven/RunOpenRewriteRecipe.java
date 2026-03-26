package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;

import java.nio.file.Path;

/**
 * Runs an OpenRewrite recipe using the rewrite-maven-plugin via Maven.
 */
public class RunOpenRewriteRecipe implements Step {

    private final RunMaven runMaven;
    private final String recipeName;

    /**
     * @param workingDirectory          the project root directory
     * @param processExecutor           the process executor for running Maven
     * @param recipeArtifactCoordinates the artifact coordinates of the recipe (e.g., "org.openrewrite.recipe:rewrite-spring:RELEASE")
     * @param activeRecipe              the fully qualified recipe name (e.g., "org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
     */
    public RunOpenRewriteRecipe(Path workingDirectory, ProcessExecutor processExecutor,
                                String recipeArtifactCoordinates, String activeRecipe) {
        this.recipeName = activeRecipe;
        this.runMaven = new RunMaven(workingDirectory, processExecutor,
                "-U",
                MavenPlugin.OPENREWRITE.goal("run"),
                "-Drewrite.recipeArtifactCoordinates=" + recipeArtifactCoordinates,
                "-Drewrite.activeRecipes=" + activeRecipe,
                "-Drewrite.exportDatatables=true");
    }

    @Override
    public void execute() throws Exception {
        runMaven.execute();
    }

    @Override
    public String name() {
        return "Run OpenRewrite Recipe: " + recipeName;
    }
}
