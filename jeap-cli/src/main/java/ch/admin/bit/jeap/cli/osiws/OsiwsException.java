package ch.admin.bit.jeap.cli.osiws;

public class OsiwsException extends RuntimeException {

    public OsiwsException(String message) {
        super(message);
    }

    public OsiwsException(String message, Throwable cause) {
        super(message, cause);
    }
}
