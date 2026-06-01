package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.migration.step.maven.PrepareForSpringBoot4ParentUpgrade;
import ch.admin.bit.jeap.cli.migration.step.maven.RemoveSpringCloudDependencyManagement;
import ch.admin.bit.jeap.cli.migration.step.maven.RunCodeFormat;
import ch.admin.bit.jeap.cli.migration.step.maven.RunOpenRewriteRecipe;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJeapDependencies;
import ch.admin.bit.jeap.cli.migration.step.springproperties.ReplaceTextInSpringProperties;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SpringBoot4Migration implements Migration {

    private final ProcessExecutor processExecutor;

    public SpringBoot4Migration(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    @Override
    public void migrate(Path root) throws Exception {
        List<Step> steps = migrationSteps(root);
        if (steps.isEmpty()) {
            return;
        }

        List<String> failedSteps = new ArrayList<>();
        // transformation steps
        for (Step step : steps) {
            try {
                step.execute();
            } catch (Exception e) {
                failedSteps.add(step.name() + " -> " + e.getMessage());
                log.warn("Step failed but migration continues: {}", step.name());
                log.warn("Cause: {}", e.getMessage());
            }
        }

        if (!failedSteps.isEmpty()) {
            throw new RuntimeException("Migration finished with step failures:\n - "
                    + String.join("\n - ", failedSteps));
        }
    }

    private List<Step> migrationSteps(Path root) {
        return List.of(
                // 0) Prepare pom.xml files: pins the jEAP parent to the Spring Boot 4 target version,
                //    replaces/removes dependencies that changed their managed state, and renames artifacts
                //    that were renamed in Spring Boot 4, so that the dependency update in step 1 can resolve
                //    all dependencies without conflicts.
                new PrepareForSpringBoot4ParentUpgrade(root),

                // 1) Update jEAP dependency versions (only locally managed, not parent-managed; including qualified versions)
                new UpdateJeapDependencies(root, processExecutor, true),

                // 2) Run OpenRewrite Spring Boot 4 migration
                //    The jeap-rewrite-recipes 1.5.3 jar includes MigrateAntPathRequestMatcher
                //    (Spring Security 7) and ChangeType recipes for ErrorPage,
                //    ConfigurableServletWebServerFactory, DefaultErrorAttributes package moves.
                new RunOpenRewriteRecipe(root, processExecutor,
                        "ch.admin.bit.jeap.openrewrite.recipe:jeap-rewrite-recipes:1.5.3,org.openrewrite.recipe:rewrite-spring:6.30.4",
                        "ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations"),

                // 3) Override secrets location prefix in spring properties
                new ReplaceTextInSpringProperties(root, "aws-secretsmanager:", "jeap-aws-secretsmanager:"),

                // 4) Format files modified by the migration using git-code-format-maven-plugin
                //    (skipped automatically if the project does not use the plugin).
                //    The plugin limits formatting to git-modified files via git diff.
                new RunCodeFormat(root, processExecutor),

                // 5) Remove spring-cloud-dependencies from dependencyManagement: managed by the
                //    jEAP Spring Boot 4 parent BOM, so an explicit import is redundant.
                new RemoveSpringCloudDependencyManagement(root)
        );
    }
}
