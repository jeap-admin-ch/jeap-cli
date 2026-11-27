package ch.admin.bit.jeap.cli.osiws;

import lombok.RequiredArgsConstructor;
import org.springframework.shell.core.command.ExitStatus;
import org.springframework.shell.core.command.exit.ExitStatusExceptionMapper;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;

@Component("osiwsExitStatusExceptionMapper")
@RequiredArgsConstructor
class OsiwsExitStatusExceptionMapper implements ExitStatusExceptionMapper {

    private final OsiwsConsole console;

    @Override
    public ExitStatus apply(Exception exception) {
        Throwable cause = unwrap(exception);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = "osiws command failed.";
        }
        console.error(message);
        return new ExitStatus(1, message);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof InvocationTargetException || current.getClass() == RuntimeException.class)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
