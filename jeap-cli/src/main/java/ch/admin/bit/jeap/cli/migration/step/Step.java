package ch.admin.bit.jeap.cli.migration.step;

public interface Step {
    void execute() throws Exception;

    default String name() {
        return getClass().getSimpleName()
                .replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}
