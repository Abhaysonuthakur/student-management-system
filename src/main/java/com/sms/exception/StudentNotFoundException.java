package com.sms.exception;

/**
 * Thrown when an operation targets a student id that does not exist.
 *
 * <p>Separate from {@link ValidationException} because the input was well formed
 * - the id is a positive number, as required - it just does not match a row.
 * The UI may want to show "no such student" differently from "that id is not a
 * number".
 */
public class StudentNotFoundException extends RuntimeException {

    public StudentNotFoundException(String message) {
        super(message);
    }
}
