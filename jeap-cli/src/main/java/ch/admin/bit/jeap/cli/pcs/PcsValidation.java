package ch.admin.bit.jeap.cli.pcs;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

final class PcsValidation {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private PcsValidation() {
    }

    static String requiredString(Object value, String fieldName, PcsProperties properties) {
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new PcsException(fieldName + " must not be blank.");
        }
        String normalized = stringValue.trim();
        int maximum = properties.getLimits().getMaxFieldLength();
        if (normalized.getBytes(StandardCharsets.UTF_8).length > maximum) {
            throw new PcsException(fieldName + " exceeds the maximum UTF-8 length of " + maximum + " bytes.");
        }
        return normalized;
    }

    static String optionalString(Object value, String fieldName, PcsProperties properties) {
        if (value == null || value instanceof String stringValue && stringValue.isBlank()) {
            return null;
        }
        return requiredString(value, fieldName, properties);
    }

    static UUID uuid(Object value, String fieldName, PcsProperties properties) {
        String normalized = requiredString(value, fieldName, properties);
        if (!UUID_PATTERN.matcher(normalized).matches()) {
            throw new PcsException("Invalid " + fieldName + " '" + normalized + "'. Must be a UUID.");
        }
        return UUID.fromString(normalized);
    }

    static void validateTaskCount(int count, PcsProperties properties) {
        int maximum = properties.getLimits().getMaxTasksPerJob();
        if (count > maximum) {
            throw new PcsException("Job exceeds the PCS maximum of " + maximum + " tasks.");
        }
    }
}
