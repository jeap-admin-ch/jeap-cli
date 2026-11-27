package ch.admin.bit.jeap.cli.osiws;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OsiwsDocumentationTest {

    @Test
    void documentationUsesTheGoldenRequestFixtures() throws Exception {
        String documentation = Files.readString(
                Path.of("..", "docs", "osiws-maintenance.md"), StandardCharsets.UTF_8);

        assertThat(documentation)
                .contains(fixture("upsert-request.yaml").strip())
                .contains(fixture("delete-request.yaml").strip());
    }

    private String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/osiws/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
