package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PrepareForSpringBoot4ParentUpgrade implements Step {

    // -------------------------------------------------------------------------
    // IMPORTANT: Keep the three constant blocks below in sync with each other.
    //
    // SPRING_BOOT_4_ALPHA_PARENT_VERSIONS pins the root pom.xml <parent> to the
    // Spring Boot 4 alpha releases of the jeap parent BOMs. These BOMs ship a
    // specific set of managed dependency versions that differs from the Spring
    // Boot 3 lineage.
    //
    // DEPENDENCIES_TO_PROJECT_MANAGE lists coordinates that are *no longer*
    // managed (or managed at a different version) by the new parent BOMs and
    // therefore need an explicit <dependencyManagement> entry in the project.
    // The resolved version is fetched from Maven Central to match whatever the
    // new parents expect. When the target parent versions change, review this
    // list: dependencies re-added to the BOM must be removed here; newly
    // unmanaged dependencies must be added.
    //
    // DEPENDENCY_REPLACEMENTS captures artifact renames and group-ID changes
    // that the new parents introduced. Again, when the target versions change,
    // verify that every rename still applies and remove any that were reverted.
    // -------------------------------------------------------------------------
    static final String JEAP_SPRING_BOOT_PARENT_SB4_VERSION = "34.6.0-alpha-springboot4";
    static final String JEAP_INTERNAL_SPRING_BOOT_PARENT_SB4_VERSION = "7.0.7-alpha-springboot4";

    private static final Map<String, String> SPRING_BOOT_4_ALPHA_PARENT_VERSIONS = Map.of(
            "jeap-spring-boot-parent", JEAP_SPRING_BOOT_PARENT_SB4_VERSION,
            "jeap-internal-spring-boot-parent", JEAP_INTERNAL_SPRING_BOOT_PARENT_SB4_VERSION
    );

    private static final List<String> DEPENDENCIES_TO_PROJECT_MANAGE = List.of(
            "org.apache.commons:commons-compress",
            "commons-io:commons-io",
            "commons-beanutils:commons-beanutils",
            "org.lz4:lz4-java",
            "at.yawk.lz4:lz4-java",
            "org.bitbucket.b_c:jose4j"
    );

    private static final List<DependencyReplacement> DEPENDENCY_REPLACEMENTS = List.of(
            DependencyReplacement.replace("com.github.tomakehurst", "wiremock-jre8-standalone",
                    "org.wiremock", "wiremock-standalone"),
            DependencyReplacement.renameArtifact("spring-boot-starter-aop", "spring-boot-starter-aspectj")
    );

    private final List<Step> subSteps;

    public PrepareForSpringBoot4ParentUpgrade(Path rootDirectory) {
        this(rootDirectory, new EnsureProjectDependencyManagement.MavenCentralVersionResolver());
    }

    PrepareForSpringBoot4ParentUpgrade(Path rootDirectory,
                                       EnsureProjectDependencyManagement.DependencyVersionResolver dependencyVersionResolver) {
        EnsureProjectDependencyManagement ensureDependencyManagement =
                new EnsureProjectDependencyManagement(rootDirectory, DEPENDENCIES_TO_PROJECT_MANAGE, dependencyVersionResolver);
        this.subSteps = List.of(
                new SetJeapParentVersion(rootDirectory, SPRING_BOOT_4_ALPHA_PARENT_VERSIONS),
                ensureDependencyManagement,
                new UpdatePomDependencies(rootDirectory, ensureDependencyManagement::projectManagedDependencies, DEPENDENCY_REPLACEMENTS)
        );
    }

    PrepareForSpringBoot4ParentUpgrade(List<Step> subSteps) {
        this.subSteps = List.copyOf(subSteps);
    }

    @Override
    public void execute() throws Exception {
        for (Step subStep : subSteps) {
            subStep.execute();
        }
    }

    @Override
    public String name() {
        return "Prepare pom.xml files for Spring Boot 4 parent upgrade";
    }
}
