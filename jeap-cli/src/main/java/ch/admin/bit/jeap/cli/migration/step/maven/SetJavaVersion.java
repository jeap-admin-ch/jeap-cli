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
     * Updates the java.version and maven.compiler.release properties in a pom.xml file to the specified version.
     * If the properties don't exist, they will be added. If the properties section doesn't exist,
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

        // Process java.version property
        xml = setProperty(xml, "java.version", javaVersion);

        // Process maven.compiler.release property
        xml = setProperty(xml, "maven.compiler.release", javaVersion);

        Files.writeString(pomPath, xml, StandardCharsets.UTF_8);
    }

    private String setProperty(String xml, String propertyName, String propertyValue) {
        String escapedPropertyName = propertyName.replace(".", "\\.");
        boolean modified = false;

        // 1) Replace existing property
        String updated = xml.replaceFirst(
                "(<" + escapedPropertyName + "\\s*>)[^<]*(</" + escapedPropertyName + ">)",
                "$1" + Matcher.quoteReplacement(propertyValue) + "$2"
        );
        if (!updated.equals(xml)) {
            return updated;
        }

        // 2) If not found, but <properties> exists: insert before </properties>
        Pattern closeProps = Pattern.compile("(?m)([ \\t]*)</properties>");
        Matcher m = closeProps.matcher(xml);
        if (m.find()) {
            String baseIndent = m.group(1); // indentation of </properties> line
            String propertyIndent = baseIndent + "    "; // one level deeper

            String insertion =
                    propertyIndent + "<" + propertyName + ">" + propertyValue + "</" + propertyName + ">\n" +
                            m.group(0); // the original </properties> line

            return m.replaceFirst(Matcher.quoteReplacement(insertion));
        }

        // 3) If no <properties> at all: add new block before </project>
        Pattern closeProject = Pattern.compile("(?m)([ \\t]*)</project>");
        m = closeProject.matcher(xml);
        if (m.find()) {
            String baseIndent = m.group(1);      // indentation of </project>
            String propsIndent = baseIndent + "  ";
            String propertyIndent = baseIndent + "    ";

            String block =
                    propsIndent + "<properties>\n" +
                            propertyIndent + "<" + propertyName + ">" + propertyValue + "</" + propertyName + ">\n" +
                            propsIndent + "</properties>\n" +
                            m.group(0); // the original </project> line

            return m.replaceFirst(Matcher.quoteReplacement(block));
        }

        // If we get here, we couldn't find a place to insert the property
        return xml;
    }
}
