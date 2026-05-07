package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.migration.step.maven.PrepareForSpringBoot4ParentUpgrade;
import ch.admin.bit.jeap.cli.migration.step.maven.RunMaven;
import ch.admin.bit.jeap.cli.migration.step.maven.RunOpenRewriteRecipe;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJeapDependencies;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJeapParent;
import ch.admin.bit.jeap.cli.migration.step.springproperties.ReplaceTextInSpringProperties;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class SpringBoot4Migration implements Migration {

    private static final int DEFAULT_MAX_AUTO_FIX_RETRIES = 25;

    private final ProcessExecutor processExecutor;
    private final RetryingMavenStepExecutor stepExecutor;

    @Autowired
    public SpringBoot4Migration(ProcessExecutor processExecutor) {
        this(processExecutor, new CopilotCliMavenFailureAutoFixer());
    }

    SpringBoot4Migration(ProcessExecutor processExecutor, MavenFailureAutoFixer mavenFailureAutoFixer) {
        this(processExecutor, new RetryingMavenStepExecutor(mavenFailureAutoFixer, "Spring Boot 4 migration"));
    }

    SpringBoot4Migration(ProcessExecutor processExecutor, RetryingMavenStepExecutor stepExecutor) {
        this.processExecutor = processExecutor;
        this.stepExecutor = stepExecutor;
    }

    @Override
    public void migrate(Path root) throws Exception {
        migrate(root, false, DEFAULT_MAX_AUTO_FIX_RETRIES);
    }

    public void migrate(Path root, boolean autoFixMavenFailures, int maxAutoFixRetries) throws Exception {
        stepExecutor.execute(root, migrationSteps(root), autoFixMavenFailures, maxAutoFixRetries);
    }

    private List<Step> migrationSteps(Path root) {
        return List.of(
                // 0) Prepare pom.xml files: pins the jEAP parent to the Spring Boot 4 alpha version,
                //    replaces/removes dependencies that changed their managed state, and renames artifacts
                //    that were renamed in Spring Boot 4, so that the parent upgrade in step 1 can resolve
                //    all dependencies without conflicts.
                new PrepareForSpringBoot4ParentUpgrade(root),

                // 1) Update jEAP parent to latest version (including qualified versions like -alpha)
                new UpdateJeapParent(root, processExecutor, true),

                // 2) Update jEAP dependency versions (only locally managed, not parent-managed; including qualified versions)
                new UpdateJeapDependencies(root, processExecutor, true),

                // 3) Run OpenRewrite Spring Boot 4 migration
                new RunOpenRewriteRecipe(root, processExecutor,
                        "ch.admin.bit.jeap.openrewrite.recipe:jeap-rewrite-recipes:1.5.0,org.openrewrite.recipe:rewrite-spring:RELEASE",
                        "ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations"),

                // 4) Override secrets location prefix in spring properties
                new ReplaceTextInSpringProperties(root, "aws-secretsmanager:", "jeap-aws-secretsmanager:"),

                // 5) Run full Maven install to verify migration after OpenRewrite
                new RunMaven(root, processExecutor, "install")
        );
    }
}
