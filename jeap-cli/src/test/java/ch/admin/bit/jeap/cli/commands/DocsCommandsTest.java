package ch.admin.bit.jeap.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocsCommandsTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultTitleIsTheGitRepositoryFolder() throws IOException {
        Path repository = Files.createDirectories(tempDir.resolve("applicationplatform-docs"));
        Files.createDirectories(repository.resolve(".git"));
        Path docs = Files.createDirectories(repository.resolve("docs/1-intro"));

        assertThat(DocsCommands.defaultTitle(docs)).isEqualTo("applicationplatform-docs");
        assertThat(DocsCommands.defaultTitle(repository)).isEqualTo("applicationplatform-docs");
    }

    @Test
    void defaultTitleOfAWorktreeWithGitFile() throws IOException {
        Path worktree = Files.createDirectories(tempDir.resolve("my-service"));
        Files.writeString(worktree.resolve(".git"), "gitdir: /somewhere/else");

        assertThat(DocsCommands.defaultTitle(worktree.resolve("docs"))).isEqualTo("my-service");
    }

    @Test
    void defaultTitleOutsideAGitRepositoryIsTheWorkingDirectory() throws IOException {
        Path folder = Files.createDirectories(tempDir.resolve("plain-folder"));

        // the temp dir could be inside a git repository on some machines
        assertThat(DocsCommands.defaultTitle(folder)).isIn("plain-folder", gitRootName(folder));
    }

    @Test
    void explicitTitleWins() {
        assertThat(DocsCommands.titleOrDefault("My docs")).isEqualTo("My docs");
    }

    private static String gitRootName(Path folder) {
        for (Path current = folder; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) {
                return current.getFileName().toString();
            }
        }
        return "plain-folder";
    }
}
