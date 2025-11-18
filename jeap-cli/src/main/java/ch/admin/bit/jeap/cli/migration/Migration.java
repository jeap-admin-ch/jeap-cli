package ch.admin.bit.jeap.cli.migration;

import java.nio.file.Path;

public interface Migration {

    void migrate(Path root) throws Exception;

}
