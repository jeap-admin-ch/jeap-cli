package ch.admin.bit.jeap.cli.migration.step.sdkmanrc;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class UpdateSdkmanrc implements Step {

    private final Path rootDirectory;
    private final String javaVersion;

    /**
     * Updates the Java version in all .sdkmanrc files.
     * <p>
     * The step finds all .sdkmanrc files in the root directory and updates
     * the java= line to use the specified Java version. For example:
     * - java=21.0.2-tem -> java=25-tem (if javaVersion is "25")
     * - java=17.0.1-ms -> java=25-ms (if javaVersion is "25")
     * <p>
     * The distribution suffix (e.g., -tem, -ms) is preserved.
     * Does nothing if no .sdkmanrc files are found.
     *
     * @param rootDirectory the root directory to search for .sdkmanrc files
     * @param javaVersion   the Java major version to set (e.g., "25")
     */
    public UpdateSdkmanrc(Path rootDirectory, String javaVersion) {
        this.rootDirectory = rootDirectory;
        this.javaVersion = javaVersion;
    }

    @Override
    public void execute() throws IOException {
        var sdkmanrcFiles = findSdkmanrcFiles();

        for (Path sdkmanrcPath : sdkmanrcFiles) {
            updateSdkmanrc(sdkmanrcPath);
        }
    }

    private List<Path> findSdkmanrcFiles() throws IOException {
        try (var stream = Files.walk(rootDirectory)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals(".sdkmanrc"))
                    .toList();
        }
    }

    private void updateSdkmanrc(Path sdkmanrcPath) throws IOException {
        String content = Files.readString(sdkmanrcPath, UTF_8);

        // Pattern to match: java=<version>-<distribution>
        // where <version> is like 21.0.2 or 21 and <distribution> is like tem, ms, etc.
        Pattern pattern = Pattern.compile("(java=)[^-\\s]+(-.+)?$", Pattern.MULTILINE);

        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1);           // "java="
            String distribution = matcher.group(2);     // "-tem" or null

            String replacement = prefix + javaVersion + (distribution != null ? distribution : "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        String newContent = result.toString();
        if (!content.equals(newContent)) {
            Files.writeString(sdkmanrcPath, newContent, UTF_8);
            log.info("Updated Java version to {} in {}", javaVersion, sdkmanrcPath);
        }
    }

    @Override
    public String name() {
        return "Update .sdkmanrc to Java " + javaVersion;
    }
}
