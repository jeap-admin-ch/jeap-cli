package ch.admin.bit.jeap.cli.migration.step.springproperties;
import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
/**
 * Replaces a given text with a new text in all Spring property files whose names match a given regex pattern.
 * Files are searched recursively starting from the root directory.
 * <p>
 * The default file name pattern matches application.yml, application.yaml, application.properties
 * and their profile-specific variants (e.g. application-dev.yml).
 */
@Slf4j
public class ReplaceTextInSpringProperties implements Step {

    public static final String SPRING_PROPERTIES_FILE_PATTERN = "application(-[\\w-]+)?\\.(yml|yaml|properties)";
    private final Path rootDirectory;
    private final String fileNamePattern;
    private final String oldText;
    private final String newText;

    public ReplaceTextInSpringProperties(Path rootDirectory, String oldText, String newText) {
        this(rootDirectory, SPRING_PROPERTIES_FILE_PATTERN, oldText, newText);
    }
    /**
     * @param rootDirectory   root directory to search recursively
     * @param fileNamePattern regex pattern matched against the file name (not the full path)
     * @param oldText         text to search for
     * @param newText         replacement text
     */
    public ReplaceTextInSpringProperties(Path rootDirectory, String fileNamePattern, String oldText, String newText) {
        this.rootDirectory = rootDirectory;
        this.fileNamePattern = fileNamePattern;
        this.oldText = oldText;
        this.newText = newText;
    }
    @Override
    public void execute() throws IOException {
        if (!Files.isDirectory(rootDirectory)) {
            return;
        }
        for (Path file : findMatchingFiles()) {
            replaceInFile(file);
        }
    }
    private List<Path> findMatchingFiles() throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                if (file.getFileName().toString().matches(fileNamePattern)) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
    private void replaceInFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.contains(oldText)) {
            Files.writeString(file, content.replace(oldText, newText), StandardCharsets.UTF_8);
            log.info("Replaced '{}' with '{}' in {}", oldText, newText, file);
        }
    }
    @Override
    public String name() {
        return "Replace Text In Spring Properties";
    }
}
