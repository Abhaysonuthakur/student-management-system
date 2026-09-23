package com.sms.exception;

/**
 * Thrown when input is malformed and the user must correct it.
 *
 * <p>Unchecked on purpose. The immediate caller cannot repair a bad email
 * address - only the person who typed it can - so forcing every caller to write
 * a {@code catch} block would be noise. The UI handles this at one boundary.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
