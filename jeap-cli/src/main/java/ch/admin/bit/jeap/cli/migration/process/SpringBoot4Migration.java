package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.maven.RunOpenRewriteRecipe;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJeapDependencies;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJeapParent;
import ch.admin.bit.jeap.cli.migration.step.springproperties.ReplaceTextInSpringProperties;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static ch.admin.bit.jeap.cli.migration.Migrations.executeStep;

@Component
@Slf4j
public class SpringBoot4Migration implements Migration {

    private final ProcessExecutor processExecutor;

    public SpringBoot4Migration(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public void migrate(Path root) throws Exception {
        // 1) Update jEAP parent to latest version (including qualified versions like -alpha)
        executeStep(new UpdateJeapParent(root, processExecutor, true));

        // 2) Update jEAP dependency versions (only locally managed, not parent-managed; including qualified versions)
        executeStep(new UpdateJeapDependencies(root, processExecutor, true));

        // 3) Run OpenRewrite Spring Boot 4 migration
        executeStep(new RunOpenRewriteRecipe(root, processExecutor,
                "ch.admin.bit.jeap.openrewrite.recipe:jeap-rewrite-recipes:1.5.0,org.openrewrite.recipe:rewrite-spring:RELEASE",
                "ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations"));

        // 4) Override secrets location prefix in spring properties
        executeStep(new ReplaceTextInSpringProperties(root,
                "aws-secretsmanager:", "jeap-aws-secretsmanager:"));
    }
}
