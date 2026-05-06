package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException;
import ch.admin.bit.jeap.cli.migration.step.maven.PrepareForSpringBoot4ParentUpgrade;
import ch.admin.bit.jeap.cli.migration.step.maven.RunOpenRewriteRecipe;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJeapDependencies;
import ch.admin.bit.jeap.cli.migration.step.maven.UpdateJeapParent;
import ch.admin.bit.jeap.cli.migration.step.springproperties.ReplaceTextInSpringProperties;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static ch.admin.bit.jeap.cli.migration.Migrations.executeStep;

@Component
@Slf4j
public class SpringBoot4Migration implements Migration {

    private static final int DEFAULT_MAX_AUTO_FIX_RETRIES = 3;

    private final ProcessExecutor processExecutor;
    private final MavenFailureAutoFixer mavenFailureAutoFixer;

    @Autowired
    public SpringBoot4Migration(ProcessExecutor processExecutor) {
        this(processExecutor, new CopilotCliMavenFailureAutoFixer());
    }

    SpringBoot4Migration(ProcessExecutor processExecutor, MavenFailureAutoFixer mavenFailureAutoFixer) {
        this.processExecutor = processExecutor;
        this.mavenFailureAutoFixer = mavenFailureAutoFixer;
    }

    @Override
    public void migrate(Path root) throws Exception {
        migrate(root, false, DEFAULT_MAX_AUTO_FIX_RETRIES);
    }

    public void migrate(Path root, boolean autoFixMavenFailures, int maxAutoFixRetries) throws Exception {
        int maxRetries = Math.max(0, maxAutoFixRetries);
        int attempt = 0;

        while (true) {
            try {
                migrateOnce(root);
                return;
            } catch (MavenCommandException e) {
                if (!autoFixMavenFailures || attempt >= maxRetries) {
                    throw e;
                }
                attempt++;
                boolean fixed = mavenFailureAutoFixer.tryFix(root, e, attempt, maxRetries);
                if (!fixed) {
                    throw e;
                }
            }
        }
    }

    private void migrateOnce(Path root) throws Exception {
        // 0) Prepare pom.xml files: pins the jEAP parent to the Spring Boot 4 alpha version,
        //    replaces/removes dependencies that changed their managed state, and renames artifacts
        //    that were renamed in Spring Boot 4, so that the parent upgrade in step 1 can resolve
        //    all dependencies without conflicts.
        executeStep(new PrepareForSpringBoot4ParentUpgrade(root));

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
