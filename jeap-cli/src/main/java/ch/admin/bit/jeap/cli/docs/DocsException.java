package ch.admin.bit.jeap.cli.docs;

/**
 * A docs preview or build failed for a reason the user can fix, e.g. a missing docs folder.
 */
public class DocsException extends RuntimeException {

    public DocsException(String message) {
        super(message);
    }

    public DocsException(String message, Throwable cause) {
        super(message, cause);
    }
}
