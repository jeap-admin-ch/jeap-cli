package ch.admin.bit.jeap.cli.migration.step.maven;

/**
 * Describes the replacement of a Maven dependency coordinate in a pom.xml file.
 * <p>
 * When {@code fromGroupId} is {@code null}, the replacement matches any groupId and
 * replaces the artifactId by a plain string substitution.
 * When {@code toGroupId} is {@code null}, the groupId is left unchanged.
 */
record DependencyReplacement(String fromGroupId, String fromArtifactId, String toGroupId, String toArtifactId) {

    /** Replaces a dependency's groupId and artifactId. */
    static DependencyReplacement replace(String fromGroupId, String fromArtifactId,
                                         String toGroupId, String toArtifactId) {
        return new DependencyReplacement(fromGroupId, fromArtifactId, toGroupId, toArtifactId);
    }

    /** Renames only the artifactId element, regardless of the groupId. */
    static DependencyReplacement renameArtifact(String fromArtifactId, String toArtifactId) {
        return new DependencyReplacement(null, fromArtifactId, null, toArtifactId);
    }
}
