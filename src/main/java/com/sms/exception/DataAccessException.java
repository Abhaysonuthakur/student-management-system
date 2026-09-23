package com.sms.exception;

/**
 * Wraps a {@link java.sql.SQLException} so that nothing above the service layer
 * ever has to know JDBC exists.
 *
 * <p>This is <strong>exception translation</strong>, and it is the reason the
 * service catches and rethrows instead of declaring {@code throws SQLException}.
 * The UI must not import {@code java.sql} - if it did, replacing MySQL would
 * break the menu.
 *
 * <p>The original exception is kept as the <em>cause</em>, so nothing is lost:
 * {@code getCause()} still returns the real {@code SQLException} with its
 * SQLState and error code, which is what you log.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
