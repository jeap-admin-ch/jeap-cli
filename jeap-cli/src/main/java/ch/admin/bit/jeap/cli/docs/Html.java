package ch.admin.bit.jeap.cli.docs;

import org.springframework.web.util.HtmlUtils;

final class Html {

    private Html() {
    }

    /**
     * Escapes text for HTML content and attribute values; non-ASCII characters stay as they are (pages are UTF-8).
     */
    static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
