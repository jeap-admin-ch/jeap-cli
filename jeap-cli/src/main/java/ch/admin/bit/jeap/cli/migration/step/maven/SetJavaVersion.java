package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetJavaVersion implements Step {

    private final Path pomPath;
    private final String javaVersion;

    /**
     * Updates the java.version property in a pom.xml file to the specified version.
     * If the property doesn't exist, it will be added. If the properties section doesn't exist,
     * it will be created.
     *
     * @param pomPath     the path to the pom.xml file
     * @param javaVersion the Java version to set (e.g., "25")
     */
    public SetJavaVersion(Path pomPath, String javaVersion) {
        this.pomPath = pomPath;
        this.javaVersion = javaVersion;
    }

    @Override
    public void execute() throws IOException {
        String xml = Files.readString(pomPath, StandardCharsets.UTF_8);
        boolean modified = false;

        // 1) Replace existing <java.version>...</java.version>
        String updated = xml.replaceFirst(
                "(<java\\.version\\s*>)[^<]*(</java\\.version>)",
                "$1" + Matcher.quoteReplacement(javaVersion) + "$2"
        );
        if (!updated.equals(xml)) {
            xml = updated;
            modified = true;
        }

        // 2) If not found, but <properties> exists: insert before </properties>
        if (!modified) {
            Pattern closeProps = Pattern.compile("(?m)([ \\t]*)</properties>");
            Matcher m = closeProps.matcher(xml);
            if (m.find()) {
                String baseIndent = m.group(1); // indentation of </properties> line
                String propertyIndent = baseIndent + "    "; // one level deeper

                String insertion =
                        propertyIndent + "<java.version>" + javaVersion + "</java.version>\n" +
                                m.group(0); // the original </properties> line

                xml = m.replaceFirst(Matcher.quoteReplacement(insertion));
                modified = true;
            }
        }

        // 3) If no <properties> at all: add new block before </project>
        if (!modified) {
            Pattern closeProject = Pattern.compile("(?m)([ \\t]*)</project>");
            Matcher m = closeProject.matcher(xml);
            if (m.find()) {
                String baseIndent = m.group(1);      // indentation of </project>
                String propsIndent = baseIndent + "  ";
                String propertyIndent = baseIndent + "    ";

                String block =
                        propsIndent + "<properties>\n" +
                                propertyIndent + "<java.version>" + javaVersion + "</java.version>\n" +
                                propsIndent + "</properties>\n" +
                                m.group(0); // the original </project> line

                xml = m.replaceFirst(Matcher.quoteReplacement(block));
                modified = true;
            }
        }

        if (modified) {
            Files.writeString(pomPath, xml, StandardCharsets.UTF_8);
        }
    }
}
