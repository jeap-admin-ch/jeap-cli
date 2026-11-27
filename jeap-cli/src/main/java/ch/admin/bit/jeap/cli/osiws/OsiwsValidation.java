package ch.admin.bit.jeap.cli.osiws;

import java.math.BigInteger;
import java.util.Map;

final class OsiwsValidation {

    /**
     * Fixed limit of the index writer for reference ids and versions (characters, not bytes).
     */
    static final int MAX_REFERENCE_LENGTH = 512;

    private OsiwsValidation() {
    }

    static String referenceId(Object value, String location) {
        String id = referenceValue(value, "id", location);
        if (id == null) {
            throw new OsiwsException("id in " + location + " must not be blank.");
        }
        return id;
    }

    static String referenceVersion(Object value, String location) {
        return referenceValue(value, "version", location);
    }

    /**
     * Ids and versions are kept as text exactly as given, apart from surrounding whitespace. Unquoted YAML integers
     * are accepted (YAML keeps leading zeros as text), other non-text scalars such as 1.50 or true would not survive
     * unchanged and must be quoted.
     */
    private static String referenceValue(Object value, String field, String location) {
        if (value == null) {
            return null;
        }
        String text;
        if (value instanceof String string) {
            text = string;
        } else if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            text = value.toString();
        } else {
            throw new OsiwsException(field + " in " + location + " is not a text value. Quote it in the YAML file "
                    + "so that it is sent exactly as written.");
        }
        String normalized = text.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_REFERENCE_LENGTH) {
            throw new OsiwsException(field + " in " + location + " is longer than " + MAX_REFERENCE_LENGTH
                    + " characters.");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new OsiwsException(field + " in " + location + " contains a control character.");
        }
        return normalized;
    }

    static String requiredText(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new OsiwsException(field + " must be set to a non-blank text value.");
        }
        return text.strip();
    }

    static Integer indexMajorVersion(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            BigInteger major = new BigInteger(value.toString());
            if (major.signum() > 0 && major.bitLength() < Integer.SIZE) {
                return major.intValue();
            }
        }
        throw new OsiwsException(field + " must be a positive integer without quotes, e.g. indexMajorVersion: 1.");
    }

    /**
     * Rejects an id seen before. The version does not make an id distinct: both entries would write the same document.
     */
    static void requireUnique(Map<String, String> firstLocationById, String id, String location) {
        String firstLocation = firstLocationById.putIfAbsent(id, location);
        if (firstLocation != null) {
            throw new OsiwsException("Duplicate id '" + id + "' in " + location + ", first in " + firstLocation
                    + ". An id may appear only once per job, also with different versions.");
        }
    }
}
