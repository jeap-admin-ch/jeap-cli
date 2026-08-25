package ch.admin.bit.jeap.cli.pcs;

public class PcsException extends RuntimeException {

    public PcsException(String message) {
        super(message);
    }

    public PcsException(String message, Throwable cause) {
        super(message, cause);
    }
}
