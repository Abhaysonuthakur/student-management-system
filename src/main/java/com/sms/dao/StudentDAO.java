package com.sms.dao;

import com.sms.model.Student;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * The data access contract for students.
 *
 * <p>This interface says <em>what</em> operations exist. It says nothing about
 * <em>how</em> they are performed - no SQL, no JDBC, not even the word "table".
 * Any storage technology could satisfy this contract.
 */
public interface StudentDAO {

    /**
     * Stores a new student.
     *
     * @return the id the database generated for the new row
     */
    long insert(Student student) throws SQLException;

    /**
     * Looks up a single student by primary key.
     *
     * @return the student, or {@link Optional#empty()} if no such id exists
     */
    Optional<Student> findById(long id) throws SQLException;

    /**
     * @return every student, ordered by id
     */
    List<Student> findAll() throws SQLException;

    /**
     * Finds students whose name or course contains the given text,
     * case-insensitively.
     */
    List<Student> search(String keyword) throws SQLException;

    /**
     * Looks up a single student by email address.
     *
     * <p>Added in Phase 8 because {@code StudentService} needs to enforce the
     * "one email, one student" rule. The interface grows to serve its callers -
     * never to mirror the table.
     *
     * @return the student, or {@link Optional#empty()} if the email is unused
     */
    Optional<Student> findByEmail(String email) throws SQLException;

    /**
     * Overwrites the stored values of an existing student.
     *
     * @return {@code true} if a row was updated, {@code false} if no student had
     *         that id
     */
    boolean update(Student student) throws SQLException;

    /**
     * @return {@code true} if a row was deleted, {@code false} if no student had
     *         that id
     */
    boolean deleteById(long id) throws SQLException;
}
