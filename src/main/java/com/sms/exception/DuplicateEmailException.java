package com.sms.exception;

/**
 * Thrown when a student is saved with an email that already belongs to someone
 * else.
 *
 * <p>Deliberately <em>not</em> a subclass of {@link ValidationException}. The
 * value itself is perfectly well formed - it is simply taken. That is a
 * conflict with existing data, not a malformed input, and the UI may want to
 * re-prompt for just the email rather than abort the whole operation. (In HTTP
 * terms: this is 409 Conflict, while validation is 400 Bad Request.)
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
