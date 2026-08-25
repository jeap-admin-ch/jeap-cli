package ch.admin.bit.jeap.cli.pcs;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PcsDocumentationTest {

    @Test
    void documentationUsesTheGoldenRequestFixtures() throws Exception {
        String documentation = Files.readString(
                Path.of("..", "docs", "pcs-maintenance.md"), StandardCharsets.UTF_8);

        assertThat(documentation)
                .contains(fixture("reevaluation-request.yaml").strip())
                .contains(fixture("backfill-request.yaml").strip())
                .contains(fixture("relation-publication-request.yaml").strip());
    }

    private String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/pcs/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
