package com.sms.service;

import com.sms.dao.StudentDAO;
import com.sms.exception.DataAccessException;
import com.sms.exception.DuplicateEmailException;
import com.sms.exception.StudentNotFoundException;
import com.sms.exception.ValidationException;
import com.sms.model.Student;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Business rules for students.
 *
 * <p>The service is the only layer allowed to decide <em>what is acceptable</em>.
 * The DAO moves data; this class decides whether the data deserves to move.
 *
 * <p>It also owns the application's exception policy: every {@link SQLException}
 * coming up from the DAO is translated into a {@link DataAccessException} here,
 * so no layer above ever imports {@code java.sql}.
 */
public class StudentService {

    private static final int MIN_AGE = 15;
    private static final int MAX_AGE = 100;

    private final StudentDAO studentDAO;

    /**
     * The DAO is handed in from outside rather than created here.
     *
     * <p>This is dependency injection without a framework: the service never
     * chooses MySQL, so a test can hand it an in-memory implementation instead.
     */
    public StudentService(StudentDAO studentDAO) {
        this.studentDAO = studentDAO;
    }

    /**
     * Registers a new student.
     *
     * @return the id the database generated
     * @throws ValidationException     if a field is malformed
     * @throws DuplicateEmailException if the email already belongs to someone
     * @throws DataAccessException     if the database could not be reached
     */
    public long addStudent(Student student) {
        cleanUp(student);
        validate(student);
        requireEmailAvailable(student.getEmail(), null);

        try {
            return studentDAO.insert(student);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the new student", e);
        }
    }

    /**
     * @throws StudentNotFoundException if no student has that id
     */
    public Student findStudent(long id) {
        requirePositiveId(id);

        try {
            return studentDAO.findById(id).orElseThrow(
                    () -> new StudentNotFoundException("No student found with id " + id));
        } catch (SQLException e) {
            throw new DataAccessException("Could not look up student " + id, e);
        }
    }

    public List<Student> findAllStudents() {
        try {
            return studentDAO.findAll();
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the student list", e);
        }
    }

    public List<Student> searchStudents(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ValidationException("Search keyword must not be empty");
        }

        try {
            return studentDAO.search(keyword.strip());
        } catch (SQLException e) {
            throw new DataAccessException("Could not run the search", e);
        }
    }

    /**
     * @return {@code true} if a row was updated, {@code false} if no student had
     *         that id
     */
    public boolean updateStudent(Student student) {
        if (student.getId() == null) {
            throw new ValidationException("Cannot update a student that has no id");
        }
        cleanUp(student);
        validate(student);
        requireEmailAvailable(student.getEmail(), student.getId());

        try {
            return studentDAO.update(student);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update student " + student.getId(), e);
        }
    }

    /**
     * @return {@code true} if a row was deleted, {@code false} if no student had
     *         that id
     */
    public boolean deleteStudent(long id) {
        requirePositiveId(id);

        try {
            return studentDAO.deleteById(id);
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete student " + id, e);
        }
    }

    /**
     * Normalises user input before it is validated or stored.
     *
     * <p>Trimming and lower-casing here means every caller - console today, REST
     * API tomorrow - gets the same treatment for free.
     */
    private void cleanUp(Student student) {
        student.setName(strip(student.getName()));
        student.setEmail(stripLower(student.getEmail()));
        student.setPhone(strip(student.getPhone()));
        student.setCourse(strip(student.getCourse()));
    }

    private void validate(Student student) {
        requireText(student.getName(), "Name");
        requireEmailShape(student.getEmail());
        requirePhoneShape(student.getPhone());
        requireText(student.getCourse(), "Course");

        if (student.getAge() < MIN_AGE || student.getAge() > MAX_AGE) {
            throw new ValidationException("Age must be between " + MIN_AGE
                    + " and " + MAX_AGE + ", got " + student.getAge());
        }
    }

    /**
     * Enforces "one email, one student".
     *
     * @param excludeId the student allowed to keep this email (the one being
     *                  updated), or {@code null} when adding a new student
     */
    private void requireEmailAvailable(String email, Long excludeId) {
        Optional<Student> existing;
        try {
            existing = studentDAO.findByEmail(email);
        } catch (SQLException e) {
            throw new DataAccessException("Could not check whether " + email + " is taken", e);
        }

        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new DuplicateEmailException("Email " + email
                    + " is already used by student id " + existing.get().getId());
        }
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new ValidationException("Student id must be positive, got " + id);
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " must not be empty");
        }
    }

    private void requireEmailShape(String email) {
        requireText(email, "Email");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Email is not a valid address: " + email);
        }
    }

    private void requirePhoneShape(String phone) {
        requireText(phone, "Phone");
        if (!phone.matches("\\d{10,15}")) {
            throw new ValidationException(
                    "Phone must be 10 to 15 digits, got: " + phone);
        }
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }

    private String stripLower(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
