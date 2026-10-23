package ch.admin.bit.jeap.cli.docs;

import java.net.URI;

/**
 * Opens a URL in the user's browser. Failures are ignored, the caller always prints the URL as well.
 */
public interface BrowserOpener {

    void open(URI uri);
}
