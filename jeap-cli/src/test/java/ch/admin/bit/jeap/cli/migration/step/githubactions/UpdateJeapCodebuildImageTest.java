package ch.admin.bit.jeap.cli.migration.step.githubactions;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UpdateJeapCodebuildImageTest {

    @TempDir
    Path tempDir;

    @Test
    void testUpdateJeapCodebuildImageWithJava21() throws Exception {
        // Given a workflow file using jeap-github-actions with jeap-codebuild-java:21
        String workflowContent = """
                name: jeap-maven-build
                
                on:
                  push:
                    branches: [ "**" ]
                
                jobs:
                  build:
                    uses: NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-maven-build.yml@v1
                    with:
                      codebuild-image: "jeap-codebuild-java:21-node-22"
                      system-name: "applicationplatform"
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating codebuild image
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then the image should be updated to Java 25
        String updatedContent = Files.readString(workflowFile);
        assertTrue(updatedContent.contains("codebuild-image: \"jeap-codebuild-java:25-node-22\""),
                "Codebuild image should be updated to Java 25");
        assertFalse(updatedContent.contains("21-node-22"),
                "Old Java 21 tag should be replaced");

        // Verify using helper method
        assertEquals("jeap-codebuild-java:25-node-22", getCodebuildImage(updatedContent));
    }

    @Test
    void testUpdateJeapCodebuildImageWithSingleQuotes() throws Exception {
        // Given a workflow file with single quotes
        String workflowContent = """
                name: build
                
                jobs:
                  build:
                    uses: org/jeap-github-actions/.github/workflows/build.yml@v1
                    with:
                      codebuild-image: 'jeap-codebuild-java:17'
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then the image should be updated
        String updatedContent = Files.readString(workflowFile);
        assertTrue(updatedContent.contains("codebuild-image: 'jeap-codebuild-java:25-node-22'"),
                "Single quotes should be preserved");

        assertEquals("jeap-codebuild-java:25-node-22", getCodebuildImage(updatedContent));
    }

    @Test
    void testUpdateJeapCodebuildImageVariants() throws Exception {
        // Given a workflow with different jeap-codebuild-java image variants
        String workflowContent = """
                name: build
                
                jobs:
                  build1:
                    uses: org/jeap-github-actions/.github/workflows/build.yml@v1
                    with:
                      codebuild-image: "jeap-codebuild-java:21"
                  build2:
                    uses: org/jeap-github-actions/.github/workflows/build.yml@v1
                    with:
                      codebuild-image: "jeap-codebuild-java-custom:17-special"
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then both images should be updated
        String updatedContent = Files.readString(workflowFile);
        assertTrue(updatedContent.contains("jeap-codebuild-java:25-node-22"),
                "jeap-codebuild-java should be updated");
        assertTrue(updatedContent.contains("jeap-codebuild-java-custom:25-node-22"),
                "jeap-codebuild-java-custom should be updated");
        assertFalse(updatedContent.contains(":21"),
                "Old tags should be replaced");
        assertFalse(updatedContent.contains(":17"),
                "Old tags should be replaced");
    }

    @Test
    void testUpdateMultipleWorkflowFiles() throws Exception {
        // Given multiple workflow files
        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);

        String workflow1 = """
                jobs:
                  build:
                    uses: org/jeap-github-actions/.github/workflows/build.yml@v1
                    with:
                      codebuild-image: "jeap-codebuild-java:21-node-22"
                """;

        String workflow2 = """
                jobs:
                  test:
                    uses: org/jeap-github-actions/.github/workflows/test.yml@v1
                    with:
                      codebuild-image: "jeap-codebuild-java:17"
                """;

        Files.writeString(workflowsDir.resolve("build.yml"), workflow1);
        Files.writeString(workflowsDir.resolve("test.yaml"), workflow2);

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then both files should be updated
        String updated1 = Files.readString(workflowsDir.resolve("build.yml"));
        assertTrue(updated1.contains("jeap-codebuild-java:25-node-22"));

        String updated2 = Files.readString(workflowsDir.resolve("test.yaml"));
        assertTrue(updated2.contains("jeap-codebuild-java:25-node-22"));
    }

    @Test
    void testNoChangeWhenNotUsingJeapGithubActions() throws Exception {
        // Given a workflow not using jeap-github-actions
        String workflowContent = """
                name: build
                
                jobs:
                  build:
                    uses: actions/setup-java@v3
                    with:
                      java-version: '21'
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);
        String originalContent = workflowContent;

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then content should remain unchanged
        String updatedContent = Files.readString(workflowFile);
        assertEquals(originalContent, updatedContent,
                "Workflow not using jeap-github-actions should not be modified");
    }

    @Test
    void testNoErrorWhenWorkflowsDirDoesNotExist() throws Exception {
        // Given a directory without .github/workflows
        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");

        // Then no error should occur
        assertDoesNotThrow(updateImage::execute,
                "Should not throw when .github/workflows does not exist");
    }

    @Test
    void testNoErrorWhenNoWorkflowFiles() throws Exception {
        // Given an empty workflows directory
        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");

        // Then no error should occur
        assertDoesNotThrow(updateImage::execute,
                "Should not throw when no workflow files exist");
    }

    @Test
    void testStepName() {
        // Given an UpdateJeapCodebuildImage step
        Step step = new UpdateJeapCodebuildImage(tempDir, "25-node-22");

        // When getting the step name
        String name = step.name();

        // Then it should return the custom name
        assertEquals("Update jEAP Codebuild Image to 25-node-22", name,
                "Step name should be 'Update jEAP Codebuild Image'");
    }

    @Test
    void testPreservesFileStructure() throws Exception {
        // Given a workflow with various elements
        String workflowContent = """
                name: jeap-maven-build
                
                on:
                  push:
                    branches: [ "**" ]
                  pull_request:
                    branches: [ main ]
                
                env:
                  JAVA_VERSION: 21
                
                jobs:
                  build:
                    uses: NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-maven-build.yml@v1
                    with:
                      codebuild-image: "jeap-codebuild-java:21-node-22"
                      system-name: "applicationplatform"
                      trigger-deployment: true
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v3
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then structure should be preserved
        String updatedContent = Files.readString(workflowFile);
        assertTrue(updatedContent.contains("name: jeap-maven-build"));
        assertTrue(updatedContent.contains("on:"));
        assertTrue(updatedContent.contains("push:"));
        assertTrue(updatedContent.contains("pull_request:"));
        assertTrue(updatedContent.contains("env:"));
        assertTrue(updatedContent.contains("JAVA_VERSION: 21"));
        assertTrue(updatedContent.contains("system-name: \"applicationplatform\""));
        assertTrue(updatedContent.contains("trigger-deployment: true"));
        assertTrue(updatedContent.contains("runs-on: ubuntu-latest"));
        assertTrue(updatedContent.contains("codebuild-image: \"jeap-codebuild-java:25-node-22\""));
    }

    @Test
    void testAddsCodebuildImageWhenMissing() throws Exception {
        // Given a workflow using jeap-github-actions without codebuild-image
        String workflowContent = """
                name: jeap-maven-build
                
                on:
                  push:
                    branches: [ "**" ]
                
                jobs:
                  build:
                    uses: NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-maven-build.yml@v1
                    with:
                      system-name: "applicationplatform"
                      trigger-deployment: true
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating codebuild image
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then the codebuild-image should be added
        String updatedContent = Files.readString(workflowFile);
        assertTrue(updatedContent.contains("codebuild-image: \"jeap-codebuild-java:25-node-22\""),
                "Codebuild image should be added to with: block");
        assertTrue(updatedContent.contains("system-name: \"applicationplatform\""),
                "Existing parameters should be preserved");
        assertTrue(updatedContent.contains("trigger-deployment: true"),
                "Existing parameters should be preserved");

        // Verify using helper method
        assertEquals("jeap-codebuild-java:25-node-22", getCodebuildImage(updatedContent));
    }

    @Test
    void testAddsCodebuildImageToPactVerifyWorkflowWhenMissing() throws Exception {
        // Given a pact-verify workflow using jeap-github-actions without codebuild-image
        String workflowContent = """
                name: jeap-pact-verify
                run-name: "Verify pact from consumer ${{ inputs.consumer-name || github.event.client_payload.consumer-name }} for provider ${{ inputs.provider-name || github.event.client_payload.provider-name }}"
                
                on:
                  repository_dispatch:
                    types:
                      - trigger-pact-verify-event
                
                  # Allows you to run this workflow manually from the Actions tab
                  workflow_dispatch:
                    inputs:
                        consumer-name:
                          required: true
                          description: 'The name of the consumer service.'
                          type: string
                        provider-name:
                          required: true
                          description: 'The name of the provider service.'
                          type: string
                        pact-url:
                          required: true
                          description: 'The URL of the pact file.'
                          type: string
                        provider-version:
                          required: false
                          description: 'The version of the provider service.'
                          type: string
                        provider-branch:
                          required: true
                          description: 'The branch of the provider service.'
                          type: string
                
                jobs:
                  verify:
                    uses: NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-pact-verify.yml@v1
                    secrets: inherit
                    with:
                      system-name: "applicationplatform"
                      consumer-name: ${{ inputs.consumer-name || github.event.client_payload.consumer-name }}
                      provider-name: ${{ inputs.provider-name || github.event.client_payload.provider-name }}
                      pact-url: ${{ inputs.pact-url || github.event.client_payload.pact-url }}
                      provider-version: ${{ inputs.provider-version || github.event.client_payload.provider-version }}
                      provider-branch: ${{ inputs.provider-branch || github.event.client_payload.provider-branch }}
                      pact-provider-tests: "PactProviderTest"
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating codebuild image
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then the codebuild-image should be added
        String updatedContent = Files.readString(workflowFile);
        assertTrue(updatedContent.contains("codebuild-image: \"jeap-codebuild-java:25-node-22\""),
                "Codebuild image should be added to with: block");
        assertTrue(updatedContent.contains("system-name: \"applicationplatform\""),
                "Existing parameters should be preserved");
        assertTrue(updatedContent.contains("pact-provider-tests: \"PactProviderTest\""),
                "Existing parameters should be preserved");
        assertTrue(updatedContent.contains("secrets: inherit"),
                "secrets: inherit should be preserved");

        // Verify using helper method
        assertEquals("jeap-codebuild-java:25-node-22", getCodebuildImage(updatedContent));
    }

    @Test
    void testAddsCodebuildImageWithCorrectIndentation() throws Exception {
        // Given a workflow with specific indentation
        String workflowContent = """
                jobs:
                  build:
                    uses: org/jeap-github-actions/.github/workflows/build.yml@v1
                    with:
                      system-name: "test"
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then codebuild-image should be added with correct indentation
        String updatedContent = Files.readString(workflowFile);
        assertTrue(updatedContent.contains("      codebuild-image: \"jeap-codebuild-java:25-node-22\""),
                "Codebuild image should have correct indentation (6 spaces)");
    }

    @Test
    void testAddsCodebuildImageToMultipleJobs() throws Exception {
        // Given a workflow with multiple jobs using jeap-github-actions
        String workflowContent = """
                jobs:
                  build:
                    uses: org/jeap-github-actions/.github/workflows/build.yml@v1
                    with:
                      system-name: "app1"
                  test:
                    uses: org/jeap-github-actions/.github/workflows/test.yml@v1
                    with:
                      system-name: "app2"
                """;

        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowFile = workflowsDir.resolve("build.yml");
        Files.writeString(workflowFile, workflowContent);

        // When updating
        Step updateImage = new UpdateJeapCodebuildImage(tempDir, "25-node-22");
        updateImage.execute();

        // Then both jobs should have codebuild-image added
        String updatedContent = Files.readString(workflowFile);
        int count = updatedContent.split("codebuild-image:", -1).length - 1;
        assertEquals(2, count, "Both jobs should have codebuild-image parameter");
    }

    private String getCodebuildImage(String workflowContent) {
        Pattern pattern = Pattern.compile("codebuild-image:\\s*['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(workflowContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
