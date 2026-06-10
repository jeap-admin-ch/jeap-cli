package ch.admin.bit.jeap.cli.migration.step.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenPluginTest {

    @Test
    void goal_versionedPlugin_usesFullyQualifiedCoordinates() {
        assertEquals("org.codehaus.mojo:versions-maven-plugin:2.19.1:update-parent",
                MavenPlugin.VERSIONS.goal("update-parent"));
    }

    @Test
    void goal_unversionedPlugin_usesFullyQualifiedCoordinates() {
        assertEquals("org.openrewrite.maven:rewrite-maven-plugin:run",
                MavenPlugin.OPENREWRITE.goal("run"));
    }
}
