package ch.admin.bit.jeap.cli.migration.step.maven;

/**
 * Maven plugins used by migration steps with their pinned versions.
 */
public enum MavenPlugin {

    VERSIONS("org.codehaus.mojo", "versions-maven-plugin", "2.19.1"),
    OPENREWRITE("org.openrewrite.maven", "rewrite-maven-plugin", null);

    private final String groupId;
    private final String artifactId;
    private final String version;

    MavenPlugin(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    /**
     * Returns the plugin goal reference for use in Maven command lines.
     * For versioned plugins: "groupId:artifactId:version:goal" (e.g., "versions:2.19.1:update-parent")
     * For unversioned plugins: "groupId:artifactId:goal" (e.g., "org.openrewrite.maven:rewrite-maven-plugin:run")
     */
    public String goal(String goal) {
        if (version != null) {
            return artifactId.replace("-maven-plugin", "") + ":" + version + ":" + goal;
        }
        return groupId + ":" + artifactId + ":" + goal;
    }
}
