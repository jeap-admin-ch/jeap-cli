package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SetJavaVersion implements Step {

    private final Path rootPath;
    private final String javaVersion;

    /**
     * Updates the java.version and maven.compiler.release properties in a pom.xml file to the specified version.
     * If the properties don't exist, they will be added. If the properties section doesn't exist,
     * it will be created.
     *
     * @param rootPath    the root directory containing the pom.xml file
     * @param javaVersion the Java version to set (e.g., "25")
     */
    public SetJavaVersion(Path rootPath, String javaVersion) {
        this.rootPath = rootPath;
        this.javaVersion = javaVersion;
    }

    @Override
    public void execute() throws IOException {
        Path pomPath = rootPath.resolve("pom.xml");

        if (!Files.isRegularFile(pomPath)) {
            log.warn("pom.xml file not found at {}, skipping java version update in pom.xml", pomPath);
            return;
        }

        String originalXml = Files.readString(pomPath, StandardCharsets.UTF_8);
        String xml = originalXml;

        // Process java.version property
        xml = setProperty(xml, "java.version", javaVersion);

        // Process maven.compiler.release property
        xml = setProperty(xml, "maven.compiler.release", javaVersion);

        // Only write if something changed
        if (!xml.equals(originalXml)) {
            Files.writeString(pomPath, xml, StandardCharsets.UTF_8);
            log.info("Updated java.version and maven.compiler.release in pom.xml");
        }
    }

    private String setProperty(String xml, String propertyName, String propertyValue) {
        String escapedPropertyName = propertyName.replace(".", "\\.");

        // 1) Check if property exists and replace it
        Pattern propertyPattern = Pattern.compile(
                "(<" + escapedPropertyName + "\\s*>)[^<]*(</" + escapedPropertyName + ">)"
        );
        Matcher propertyMatcher = propertyPattern.matcher(xml);
        if (propertyMatcher.find()) {
            // Property exists, replace its value (even if it's the same)
            return propertyMatcher.replaceFirst(
                    "$1" + Matcher.quoteReplacement(propertyValue) + "$2"
            );
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

        // 3) If no <properties> at all: add new block before <dependencies> (if found), otherwise before </project>
        Pattern openDependencies = Pattern.compile("(?m)([ \\t]*)<dependencies>");
        m = openDependencies.matcher(xml);
        if (m.find()) {
            String baseIndent = m.group(1);      // indentation of <dependencies>
            String propsIndent = baseIndent;
            String propertyIndent = baseIndent + "    ";

            String block =
                    propsIndent + "<properties>\n" +
                            propertyIndent + "<" + propertyName + ">" + propertyValue + "</" + propertyName + ">\n" +
                            propsIndent + "</properties>\n\n" +
                            m.group(0); // the original <dependencies> line

            return m.replaceFirst(Matcher.quoteReplacement(block));
        }

        // 4) If no <dependencies>: add new block before </project>
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
